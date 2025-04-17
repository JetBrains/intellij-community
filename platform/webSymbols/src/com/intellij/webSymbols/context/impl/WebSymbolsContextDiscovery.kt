// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package com.intellij.webSymbols.context.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES
import com.intellij.openapi.vfs.VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
import com.intellij.openapi.vfs.findFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.lazyUnsafe
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.context.WebSymbolContextChangeListener
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.context.WebSymbolsContextKindRules.EnablementRules
import com.intellij.webSymbols.context.WebSymbolsContextSourceProximityProvider
import com.intellij.webSymbols.context.WebSymbolsContextSourceProximityProvider.Companion.mergeProximity
import com.intellij.webSymbols.context.WebSymbolsContextSourceProximityProvider.SourceKind
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorFactoryImpl
import com.intellij.webSymbols.utils.findOriginalFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.component1
import kotlin.collections.component2

private val WEB_SYMBOLS_CONTEXT_EP get() = WebSymbolsContext.WEB_SYMBOLS_CONTEXT_EP as WebSymbolsContextProviderExtensionCollector
private val CONTEXT_RELOAD_MARKER_KEY = Key<Any>("web.isContext.reloadMarker")
private val reloadMonitor = Any()
private val LOG = Logger.getInstance(WebSymbolsContext::class.java)

@RequiresReadLock
internal fun findWebSymbolsContext(kind: ContextKind, location: PsiElement): ContextName? =
  forPsiLocation(location) {
    findWebSymbolsContext(kind, it)
  }

@RequiresReadLock
internal fun findWebSymbolsContext(kind: ContextKind, location: VirtualFile, project: Project): ContextName? =
  forVfsLocation(project, location) {
    findWebSymbolsContext(kind, it)
  }

@RequiresReadLock
internal fun buildWebSymbolsContext(location: PsiElement): WebSymbolsContext =
  forPsiLocation(location) { locationInfo ->
    allKinds(locationInfo.rulesConfigInDir, locationInfo.fileConfigInDir)
      .asSequence()
      .mapNotNull { kind -> findWebSymbolsContext(kind, locationInfo)?.let { Pair(kind, it) } }
      .toMap()
      .let { WebSymbolsContext.create(it) }
  } ?: WebSymbolsContext.empty()

private fun findWebSymbolsContext(kind: ContextKind, locationInfo: LocationInfo): ContextName? =
  locationInfo.psiFile
    ?.let { findEnabledFromProviders(kind, it) }
  ?: locationInfo.file
    ?.let { findEnabledFromProviders(kind, it, locationInfo.project) }
  ?: withContextChangeCheck(kind, locationInfo)

private fun <T> forPsiLocation(location: PsiElement, action: (LocationInfo) -> T): T? {
  if (!location.isValid) return null
  val project = location.project
  val psiFile = location.takeIf { it !is PsiDirectory }
    ?.let { InjectedLanguageManager.getInstance(project).getTopLevelFile(it) }

  return forVfsLocation(
    project,
    if (location is PsiDirectory)
      location.virtualFile
    else
      location.containingFile?.originalFile?.viewProvider?.virtualFile ?: return null,
    psiFile, action
  )
}

private fun <T> forVfsLocation(
  project: Project,
  location: VirtualFile,
  psiFile: PsiFile? = null,
  action: (LocationInfo) -> T
): T? {
  ProgressManager.checkCanceled()
  if (project.isDisposed) return null
  val virtualFile = findOriginalFile(location)?.takeIf { it.isInLocalFileSystem }

  val dir =
    when {
      virtualFile == null -> @Suppress("DEPRECATION") project.baseDir
      virtualFile.isDirectory -> virtualFile
      else -> virtualFile.parent?.takeIf { it.isValid }
    } ?: return null

  return action(LocationInfo(project, dir, psiFile, virtualFile?.takeIf { !it.isDirectory }))
}

private class LocationInfo(
  val project: Project,
  val dir: VirtualFile,
  val psiFile: PsiFile?,
  val file: VirtualFile?,
) {
  val contextInfo: WebSymbolsContextDiscoveryInfo = project.contextInfo
  val rulesConfigInDir: ContextRulesConfigInDir by lazyUnsafe { contextInfo.getContextRulesConfigInDir(dir) }
  val fileConfigInDir: ContextFileConfigInDir by lazyUnsafe { contextInfo.getContextFileConfigInDir(dir) }
}

private fun allKinds(rulesConfigInDir: ContextRulesConfigInDir?, fileConfigInDir: ContextFileConfigInDir?): Set<String> =
  (rulesConfigInDir?.kinds ?: emptySet()) + (fileConfigInDir?.kinds ?: emptySet()) + WEB_SYMBOLS_CONTEXT_EP.allKinds()

private fun findContextInDirOrFileCached(kind: ContextKind, locationInfo: LocationInfo): ContextName? {
  val project = locationInfo.project
  val contextInfo = locationInfo.contextInfo
  val dir = locationInfo.dir
  val file = locationInfo.file
  val rulesConfigInDir = locationInfo.rulesConfigInDir
  val fileConfigInDir = locationInfo.fileConfigInDir

  assert(file == null || !file.isDirectory) { "\'${file?.path}\' is not a file." }
  // File config overrides any automatic detection
  fileConfigInDir
    .findByFileName(kind, file?.name)
    ?.let { return if (it == WebSymbolsContext.VALUE_NONE) null else it }

  val webSymbolsContextKindDisableRules = rulesConfigInDir.rules[kind]?.disable
  file
    ?.let { rulesConfigInDir.findByFileName(kind, it) }
    ?.takeIf { !isForbiddenFromProviders(kind, it, file, project, webSymbolsContextKindDisableRules?.get(it)) }
    ?.let { return it }

  val proximityPerContextFromRulesConfig = rulesConfigInDir.getProximityPerContext(kind)
  val proximityPerContextFromExtensions = WEB_SYMBOLS_CONTEXT_EP.allOf(kind).asSequence()
    .mapNotNull {
      val name = it.key
      val proximity = contextInfo.getProximityFromExtensions(dir, kind, name)
      proximity?.let { Pair(name, proximity) }
    }
    .toMap(HashMap())

  return proximityPerContextFromRulesConfig.keys
    .asSequence().plus(proximityPerContextFromExtensions.keys)
    .distinct()
    .map {
      val a = proximityPerContextFromRulesConfig[it]
      val b = proximityPerContextFromExtensions[it]?.toDouble()
      Pair(it, if (a != null && b != null) a.coerceAtMost(b) else a ?: b)
    }
    .filter {
      file == null || !isForbiddenFromProviders(kind, it.first, file, project, webSymbolsContextKindDisableRules?.get(it.first))
    }
    .minByOrNull { it.second!! }
    ?.first
    ?.takeIf { file == null || !isAnyForbidden(kind, file, project) }
}

private fun calcProximityPerContextFromRules(project: Project,
                                             directory: VirtualFile,
                                             enableWhen: Map<ContextKind, Map<ContextName, List<EnablementRules>>>)
  : Pair<Map<ContextKind, Map<ContextName, Double>>, Set<ModificationTracker>> {

  val result = mutableMapOf<ContextKind, MutableMap<String, Double>>()
  val modificationTrackers = mutableSetOf<ModificationTracker>()

  val sourceKindToDepsToContext = enableWhen
    .flatMap { (contextKind, map) ->
      map.entries.flatMap { (contextName, value) ->
        value.asSequence().flatMap { rule ->
          // Check enabled IDE libraries
          rule.ideLibraries.asSequence()
            .map { Pair(SourceKind.IdeLibrary, it) } +
          // Check project tool executables
          rule.projectToolExecutables.asSequence()
            .map { Pair(SourceKind.ProjectToolExecutable, it) } +
          // Check package manager dependencies
          rule.pkgManagerDependencies.asSequence()
            .flatMap { dep ->
              val pkgManagerSourceKind = SourceKind.PackageManagerDependency(dep.key)
              dep.value.asSequence().map { Pair(pkgManagerSourceKind, it) }
            }
        }.map { Triple(it.first, it.second, Pair(contextKind, contextName)) }
      }
    }
    .groupBy({ it.first }, { Pair(it.second, it.third) })
    .mapValues { perSourceKind -> perSourceKind.value.groupBy({ it.first }, { it.second }) }

  sourceKindToDepsToContext.forEach { (sourceKind, depsToContext) ->
    WebSymbolsContextSourceProximityProvider.calculateProximity(project, directory, depsToContext.keys, sourceKind)
      .let {
        it.dependency2proximity.forEach { (lib, proximity) ->
          depsToContext[lib]?.forEach { (contextKind, contextName) ->
            result
              .getOrPut(contextKind) { mutableMapOf() }
              .merge(contextName, proximity, ::mergeProximity)
          }
        }
        modificationTrackers.addAll(it.modificationTrackers)
      }
  }

  return Pair(result.mapValues { (_, map) -> map.toMap() }, modificationTrackers)
}

private fun loadContextRulesConfiguration(project: Project, directory: VirtualFile): ContextRulesConfigInDir {
  val queryExecutorFactory = WebSymbolsQueryExecutorFactory.getInstance(project) as WebSymbolsQueryExecutorFactoryImpl
  val (rules, tracker) = queryExecutorFactory.getContextRules(project, directory)

  val flatRules = rules.keySet().associateBy({ it }, { kind ->
    val kindRules = rules[kind]
    val enableWhen = kindRules
      .flatMap { config ->
        config.enable.entries.asSequence()
          .flatMap { (name, rules) -> rules.asSequence().map { Pair(name, it) } }
      }
      .groupBy({ it.first }, { it.second })
    val disableWhen = kindRules
      .flatMap { config ->
        config.disable.entries.asSequence()
          .flatMap { (name, rules) -> rules.asSequence().map { Pair(name, it) } }
      }
      .groupBy({ it.first }, { it.second })
    WebSymbolsContextKindRules.create(enableWhen, disableWhen)
  })

  return ContextRulesConfigInDir(project, directory, flatRules, listOf(tracker))
}

private class ContextRulesConfigInDir(val project: Project,
                                      val directory: VirtualFile,
                                      val rules: Map<ContextKind, WebSymbolsContextKindRules>,
                                      val dependencies: List<Any>) {

  private val contextByFile = ConcurrentHashMap<Pair<ContextKind, String>, ContextName>()

  private val proximityCache = CachedValuesManager.getManager(project).createCachedValue {
    val result = calcProximityPerContextFromRules(project, directory, rules.mapValues { it.value.enable })
    CachedValueProvider.Result.create(result.first, result.second + dependencies)
  }

  val kinds: Set<ContextKind> get() = rules.keys

  fun getProximityPerContext(kind: String): Map<String, Double> = proximityCache.value[kind] ?: emptyMap()

  fun findByFileName(kind: String, file: VirtualFile): ContextName? {
    val fileName = file.name
    return contextByFile.computeIfAbsent(Pair(kind, fileName)) {
      val rules = rules[kind]
      rules?.enable?.keys
        ?.find { contextName ->
          rules.enable[contextName]?.any {
            matchFileExt(fileName, it.fileExtensions) || matchFileName(fileName, it.fileNamePatterns)
          } == true
          && rules.disable[contextName]?.any {
            matchFileExt(fileName, it.fileExtensions) || matchFileName(fileName, it.fileNamePatterns)
          } != true
        } ?: ""
    }.takeIf { it.isNotBlank() }
  }

}

private fun loadContextFilesConfiguration(directory: VirtualFile): ContextFileConfigInDir {
  val dependencies = mutableListOf<Any>(VFS_STRUCTURE_MODIFICATIONS)
  val contexts = generateSequence(Pair(directory, 0)) { (dir, proximity) -> dir.parent?.let { Pair(it, proximity - 1) } }
    .flatMap { (dir, proximity) ->
      dir.findFile(WebSymbolsContext.WEB_SYMBOLS_CONTEXT_FILE)
        ?.let {
          dependencies.add(it)
          WebSymbolsContextFileData.getOrCreate(it)
        }
        ?.getContextsInDirectory(directory, proximity)
      ?: emptyList()
    }
    .sortedBy { -it.priority }
    .toList()
  return ContextFileConfigInDir(contexts, dependencies)
}

private class ContextFileConfigInDir(
  val contexts: List<WebSymbolsContextFileData.DirectoryContext>,
  val dependencies: List<Any>
) {
  val kinds: Set<ContextKind> = setOf()

  fun findByFileName(kind: ContextKind, fileName: String?): ContextName? =
    contexts.firstNotNullOfOrNull { directoryContext ->
      directoryContext.context[kind]?.takeIf { directoryContext.matches(fileName) }
    }
}

private fun isForbiddenFromProviders(kind: ContextKind,
                                     name: ContextName,
                                     file: VirtualFile,
                                     project: Project,
                                     disableWhen: List<WebSymbolsContextKindRules.DisablementRules>?): Boolean =
  WEB_SYMBOLS_CONTEXT_EP.allFor(kind, name).any { it.isForbidden(file, project) }
  || disableWhen?.any { matchFileName(file.name, it.fileNamePatterns) || matchFileExt(file.name, it.fileExtensions) } == true

private fun isAnyForbidden(kind: ContextKind, context: VirtualFile, project: Project): Boolean =
  WEB_SYMBOLS_CONTEXT_EP.forAny(kind).any { it.isForbidden(context, project) }

private fun findEnabledFromProviders(kind: ContextKind, psiFile: PsiFile): ContextName? =
  WEB_SYMBOLS_CONTEXT_EP.allOf(kind).entries
    .firstOrNull { (_, providers) -> providers.any { it.isEnabled(psiFile) } }
    ?.key

private fun findEnabledFromProviders(kind: ContextKind, file: VirtualFile, project: Project): ContextName? =
  WEB_SYMBOLS_CONTEXT_EP.allOf(kind).entries
    .firstOrNull { (_, providers) -> providers.any { it.isEnabled(file, project) } }
    ?.key

private fun webContextProximityFromProviders(kind: ContextKind,
                                             name: ContextName,
                                             project: Project,
                                             directory: VirtualFile): CachedValueProvider.Result<Int?> {
  val dependencies = mutableSetOf<Any>()
  var proximity: Int? = null
  for (provider in WEB_SYMBOLS_CONTEXT_EP.allFor(kind, name)) {
    val result = provider.isEnabled(project, directory)
    result.value?.let {
      if (proximity == null) {
        proximity = it
      }
      else {
        proximity!!.coerceAtMost(it)
      }
    }
    dependencies.addAll(result.dependencyItems)
  }
  if (dependencies.isEmpty()) {
    dependencies.add(ModificationTracker.NEVER_CHANGED)
  }
  return CachedValueProvider.Result(proximity, *dependencies.toTypedArray())
}

private const val EMPTY_CONTEXT = "%EMPTY%"

private fun withContextChangeCheck(kind: ContextKind, locationInfo: LocationInfo): ContextName? {
  val currentState = findContextInDirOrFileCached(kind, locationInfo)
    ?.takeIf { it != WebSymbolsContext.VALUE_NONE }

  val contextFile = locationInfo.file ?: locationInfo.dir
  val prevState = locationInfo.contextInfo.updateContext(contextFile, kind, currentState ?: EMPTY_CONTEXT)
  if (prevState != null && prevState != (currentState ?: EMPTY_CONTEXT)) {
    reloadProject(kind, prevState.takeIf { it != EMPTY_CONTEXT } ?: "none",
                  currentState ?: "none", locationInfo.project, contextFile)
  }
  return currentState
}

private fun matchFileName(fileName: String, fileNamePatterns: List<Regex>): Boolean =
  fileNamePatterns.any { it.matches(fileName) }

private fun matchFileExt(fileName: String, fileExtensions: List<String>): Boolean {
  if (fileExtensions.isEmpty()) return false
  val ext = FileUtilRt.getExtension(fileName)
  return fileExtensions.any { ext == it }
}

private fun reloadProject(kind: ContextKind, prevState: ContextName, newState: ContextName, project: Project, file: VirtualFile) {
  synchronized(reloadMonitor) {
    if (project.getUserData(CONTEXT_RELOAD_MARKER_KEY) != null) {
      return
    }
    project.putUserData(CONTEXT_RELOAD_MARKER_KEY, true)
  }
  LOG.info("Reloading project ${project.name} on Web Symbols $kind context change (${prevState} -> ${newState}) in file ${file.path}.")
  ApplicationManager.getApplication().invokeLater(
    Runnable {
      WriteAction.run<RuntimeException> {
        try {
          ProjectRootManagerEx.getInstanceEx(project)
            .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.TOTAL_RESCAN)
        }
        finally {
          project.putUserData(CONTEXT_RELOAD_MARKER_KEY, null)
        }
      }
    },
    ModalityState.nonModal(),
    Condition<Any> {
      project.disposed.value(null).also {
        // Clear the flag in case the project is recycled
        if (it) project.putUserData(CONTEXT_RELOAD_MARKER_KEY, null)
      }
    })
}

private val Project.contextInfo
  get() = service<WebSymbolsContextDiscoveryInfo>()

@Service(Service.Level.PROJECT)
private class WebSymbolsContextDiscoveryInfo(private val project: Project, private val cs: CoroutineScope) : Disposable {

  private val previousContext = ConcurrentHashMap<ContextKind, MutableMap<VirtualFile, String>>()
  private val cachedData = ContainerUtil.createConcurrentWeakMap<VirtualFile, CachedData>()

  init {
    val messageBus = project.messageBus.connect(this)
    messageBus.subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        previousContext.clear()
        cachedData.clear()
        thisLogger().info("Notifying that Web Symbol context may have changed due to roots changes.")
        project.messageBus.syncPublisher(WebSymbolContextChangeListener.TOPIC).contextMayHaveChanged()
      }
    })
    messageBus.subscribe(WebSymbolContextChangeListener.TOPIC, WebSymbolContextChangeListener {
      DaemonCodeAnalyzer.getInstance(project).restart()
    })
    messageBus.subscribe(VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        val wsFile = events.find { it.file?.name == WebSymbolsContext.WEB_SYMBOLS_CONTEXT_FILE }
        if (wsFile != null) {
          thisLogger().info("Notifying that Web Symbol context may have changed due to changes in ${wsFile.path}.")
          cs.launch {
            project.messageBus.syncPublisher(WebSymbolContextChangeListener.TOPIC).contextMayHaveChanged()
          }
        }
      }
    })
  }

  fun getProximityFromExtensions(dir: VirtualFile, kind: ContextKind, name: ContextName): Int? =
    getCachedDataForDir(dir)
      .proximity
      .computeIfAbsent(Pair(kind, name)) {
        CachedValuesManager.getManager(project).createCachedValue {
          webContextProximityFromProviders(kind, name, project, dir)
        }
      }
      .value

  fun updateContext(contextFile: VirtualFile, kind: ContextKind, name: ContextName): String? =
    previousContext.computeIfAbsent(kind) { ContainerUtil.createConcurrentWeakMap() }
      .put(contextFile, name)

  fun getContextRulesConfigInDir(dir: VirtualFile): ContextRulesConfigInDir =
    getCachedDataForDir(dir).rulesConfig.value

  fun getContextFileConfigInDir(dir: VirtualFile): ContextFileConfigInDir =
    getCachedDataForDir(dir).filesConfig.value

  private fun getCachedDataForDir(dir: VirtualFile): CachedData =
    cachedData.computeIfAbsent(dir) {
      CachedData(project, dir)
    }

  override fun dispose() {}

  private class CachedData(private val project: Project,
                           private val directory: VirtualFile) {

    val proximity: MutableMap<Pair<ContextKind, ContextName>, CachedValue<Int?>> =
      ConcurrentHashMap<Pair<ContextKind, ContextName>, CachedValue<Int?>>()

    val rulesConfig: CachedValue<ContextRulesConfigInDir> =
      CachedValuesManager.getManager(project).createCachedValue {
        val result = loadContextRulesConfiguration(project, directory)
        CachedValueProvider.Result.create(result, result.dependencies)
      }

    val filesConfig: CachedValue<ContextFileConfigInDir> =
      CachedValuesManager.getManager(project).createCachedValue {
        val result = loadContextFilesConfiguration(directory)
        CachedValueProvider.Result.create(result, result.dependencies)
      }

  }
}