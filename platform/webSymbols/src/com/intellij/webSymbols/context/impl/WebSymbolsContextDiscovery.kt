// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package com.intellij.webSymbols.context.impl

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.webSymbols.ContextKind
import com.intellij.webSymbols.ContextName
import com.intellij.webSymbols.context.DependencyProximityProvider
import com.intellij.webSymbols.context.DependencyProximityProvider.Companion.mergeProximity
import com.intellij.webSymbols.context.DependencyProximityProvider.DependenciesKind
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.WEB_SYMBOLS_CONTEXT_EP
import com.intellij.webSymbols.context.WebSymbolsContextKindRules
import com.intellij.webSymbols.context.WebSymbolsContextKindRules.EnablementRules
import com.intellij.webSymbols.context.WebSymbolsContextProvider
import com.intellij.webSymbols.framework.impl.WebSymbolsFrameworkExtension
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory
import com.intellij.webSymbols.query.impl.WebSymbolsQueryExecutorFactoryImpl
import com.intellij.webSymbols.utils.findOriginalFile
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.Pair
import kotlin.collections.component1
import kotlin.collections.component2

private val CONTEXT_KEY: Key<MutableMap<String, MutableMap<String, CachedValue<Int?>>>> = Key("web.isContext")
private val PREV_CONTEXT_KEY = Key<MutableMap<VirtualFile, MutableMap<String, String>>>("web.isContext.prev")
private val CONTEXT_RELOAD_MARKER_KEY = Key<Any>("web.isContext.reloadMarker")
private val reloadMonitor = Any()
private val LOG = Logger.getInstance(WebSymbolsContext::class.java)

internal val WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED = WebSymbolsFrameworkExtension<WebSymbolsContextProvider>(
  "com.intellij.javascript.web.context")

internal fun findWebSymbolsContext(kind: String, location: PsiElement): ContextName? {
  if (!location.isValid) {
    return null
  }
  if (location is PsiDirectory) {
    return withContextChangeCheck(kind, location, null, getContextConfigInDir(location))
  }
  val psiFile = InjectedLanguageManager.getInstance(location.project).getTopLevelFile(location) ?: return null
  findEnabledFromProviders(kind, psiFile)?.let { return it }

  val file = findOriginalFile(psiFile.originalFile.virtualFile)
  @Suppress("DEPRECATION")
  return findWebSymbolsContext(
    kind,
    (if (file != null && file.isInLocalFileSystem) file
    else psiFile.project.baseDir) ?: return null,
    psiFile.project)
}

internal fun findWebSymbolsContext(kind: String, location: VirtualFile, project: Project): ContextName? {
  if (project.isDisposed) return null
  val dirContext = location.isDirectory
  val file: VirtualFile? = findOriginalFile(location)
  if (file?.isDirectory == false) {
    findEnabledFromProviders(kind, file, project)
      ?.let { return it }
  }
  val psiDir = (if (dirContext) file else file?.parent)
                 ?.let { if (it.isValid) PsiManager.getInstance(project).findDirectory(it) else null }
               ?: return null
  return withContextChangeCheck(kind, psiDir, file, getContextConfigInDir(psiDir))
}

internal fun buildWebSymbolsContext(location: PsiElement): WebSymbolsContext {
  if (!location.isValid) {
    return WebSymbolsContext.empty()
  }

  val contextMap = if (location is PsiDirectory) {
    val configInDir = getContextConfigInDir(location)
    val allKinds = configInDir.kinds + WEB_SYMBOLS_CONTEXT_EP.allKinds()

    allKinds.mapNotNull { kind ->
      withContextChangeCheck(kind, location, null, configInDir)
        ?.let { Pair(kind, it) }
    }
  }
  else {
    val psiFile = InjectedLanguageManager.getInstance(location.project)
      .getTopLevelFile(location)

    val virtualFile = findOriginalFile(psiFile.originalFile.virtualFile)

    val psiDir = virtualFile
                   ?.parent
                   ?.takeIf { it.isValid }
                   ?.let { PsiManager.getInstance(location.project).findDirectory(it) }

    val configInDir = psiDir?.let { getContextConfigInDir(psiDir) }

    val allKinds = (configInDir?.kinds ?: emptySet()) + WEB_SYMBOLS_CONTEXT_EP.allKinds()

    @Suppress("DEPRECATION")
    val checkLocation = if (virtualFile != null && virtualFile.isInLocalFileSystem) virtualFile else psiFile.project.baseDir

    allKinds.mapNotNull { kind ->
      findEnabledFromProviders(kind, psiFile)
        ?.let { return@mapNotNull Pair(kind, it) }

      if (checkLocation?.isDirectory == false) {
        findEnabledFromProviders(kind, checkLocation, psiFile.project)
          ?.let { return@mapNotNull Pair(kind, it) }
      }

      if (checkLocation == null || psiDir == null || configInDir == null)
        return@mapNotNull null

      withContextChangeCheck(kind, psiDir, checkLocation, configInDir)
        ?.let { Pair(kind, it) }
    }
  }
  return WebSymbolsContext.create(contextMap.toMap())
}

private fun findContextInDirOrFileCached(kind: String,
                                         directory: PsiDirectory,
                                         file: VirtualFile?,
                                         configInDir: ContextConfigInDir): ContextName? {
  val project = directory.project

  file
    ?.let { configInDir.findByFileName(kind, it) }
    ?.let { return it }
  val proximityPerContextFromConfig = configInDir.getProximityPerContext(kind)

  val proximityPerContextFromExtensionsMap =
    ((directory.getUserData(CONTEXT_KEY)
      ?: (directory as UserDataHolderEx).putUserDataIfAbsent(CONTEXT_KEY, ConcurrentHashMap())))
      .computeIfAbsent(kind) { ConcurrentHashMap() }

  val proximityPerContextFromExtensions = WEB_SYMBOLS_CONTEXT_EP.allOf(kind).asSequence()
    .map { it.key }
    .map { name ->
      Pair(name,
           proximityPerContextFromExtensionsMap.computeIfAbsent(name) {
             CachedValuesManager.getManager(directory.project).createCachedValue {
               webContextProximityFromProviders(kind, name, directory)
             }
           }.value
      )
    }
    .filter { it.second != null }
    .toMap()

  return proximityPerContextFromConfig.keys
    .asSequence().plus(proximityPerContextFromExtensions.keys)
    .distinct()
    .map {
      val a = proximityPerContextFromConfig[it]
      val b = proximityPerContextFromExtensions[it]?.toDouble()
      Pair(it, if (a != null && b != null) a.coerceAtMost(b) else a ?: b)
    }
    .filter {
      file == null || !isForbiddenFromProviders(kind, it.first, file, directory.project,
                                                configInDir.rules[kind]?.disable?.get(it.first))
    }
    .minByOrNull { it.second!! }
    ?.first
    ?.takeIf { file == null || !isAnyForbidden(kind, file, project) }
}

private fun getContextConfigInDir(directory: PsiDirectory): ContextConfigInDir =
  CachedValuesManager.getCachedValue(directory) {
    val result = loadContextConfiguration(directory)
    CachedValueProvider.Result.create(result, result.dependencies)
  }

private fun calcProximityPerContextFromRules(directory: PsiDirectory,
                                             enableWhen: Map<ContextKind, Map<ContextName, List<EnablementRules>>>)
  : Pair<Map<ContextKind, Map<ContextName, Double>>, Set<ModificationTracker>> {

  val result = mutableMapOf<ContextKind, MutableMap<String, Double>>()
  val modificationTrackers = mutableSetOf<ModificationTracker>()

  fun calculateProximity(listAccessor: (EnablementRules) -> List<String>, dependenciesKind: DependenciesKind) {
    val depsToContext = enableWhen
      .flatMap { (contextKind, map) ->
        map.entries.flatMap { (contextName, value) ->
          value.asSequence().flatMap(listAccessor).map { Pair(it, Pair(contextKind, contextName)) }
        }
      }
      .groupBy({ it.first }, { it.second })

    DependencyProximityProvider.calculateProximity(directory, depsToContext.keys, dependenciesKind)
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

  // Check enabled IDE libraries
  calculateProximity({ it.ideLibraries }, DependenciesKind.IdeLibrary)

  // Check packages by `package.json` entries
  calculateProximity({ it.pkgManagerDependencies }, DependenciesKind.PackageManagerDependency)

  return Pair(result.mapValues { (_, map) -> map.toMap() }, modificationTrackers)
}

private fun loadContextConfiguration(directory: PsiDirectory): ContextConfigInDir {
  val queryExecutorFactory = WebSymbolsQueryExecutorFactory.getInstance(directory.project) as WebSymbolsQueryExecutorFactoryImpl
  val (rules, tracker) = queryExecutorFactory.getContextRules(directory)

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

  return ContextConfigInDir(directory, flatRules, listOf(tracker))
}

private class ContextConfigInDir(val directory: PsiDirectory,
                                 val rules: Map<ContextKind, WebSymbolsContextKindRules>,
                                 val dependencies: List<Any>) {

  private val contextByFile = ConcurrentHashMap<String, ContextName>()

  private val proximityCache = CachedValuesManager.getManager(directory.project).createCachedValue {
    val result = calcProximityPerContextFromRules(directory, rules.mapValues { it.value.enable })
    CachedValueProvider.Result.create(result.first, dependencies + result.second)
  }

  val kinds: Set<ContextKind> get() = rules.keys

  fun getProximityPerContext(kind: String): Map<String, Double> = proximityCache.value[kind] ?: emptyMap()

  fun findByFileName(kind: String, file: VirtualFile): ContextName? =
    contextByFile.computeIfAbsent("$kind:::" + file.name) { fileName ->
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

private fun isForbiddenFromProviders(kind: String,
                                     name: String,
                                     file: VirtualFile,
                                     project: Project,
                                     disableWhen: List<WebSymbolsContextKindRules.DisablementRules>?): Boolean =
  WEB_SYMBOLS_CONTEXT_EP.allFor(kind, name).any { it.isForbidden(file, project) }
  || (kind == KIND_FRAMEWORK && WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.allFor(name).any { it.isForbidden(file, project) })
  || disableWhen?.any { matchFileName(file.name, it.fileNamePatterns) || matchFileExt(file.name, it.fileExtensions) } == true

private fun isAnyForbidden(kind: String, context: VirtualFile, project: Project): Boolean =
  WEB_SYMBOLS_CONTEXT_EP.forAny(kind).any { it.isForbidden(context, project) }
  || (kind == KIND_FRAMEWORK && WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.forAny().any { it.isForbidden(context, project) })

private fun findEnabledFromProviders(kind: String, psiFile: PsiFile): ContextName? =
  (WEB_SYMBOLS_CONTEXT_EP.allOf(kind).entries
     .firstOrNull { (_, providers) -> providers.any { it.isEnabled(psiFile) } }
     ?.key
   ?: if (kind == KIND_FRAMEWORK)
     WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.all.entries
       .firstOrNull { (_, providers) -> providers.any { it.isEnabled(psiFile) } }
       ?.key?.id
   else null)

private fun findEnabledFromProviders(kind: String, file: VirtualFile, project: Project): ContextName? =
  (WEB_SYMBOLS_CONTEXT_EP.allOf(kind).entries
     .firstOrNull { (_, providers) -> providers.any { it.isEnabled(file, project) } }
     ?.key
   ?: if (kind == KIND_FRAMEWORK)
     WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.all.entries
       .firstOrNull { (_, providers) -> providers.any { it.isEnabled(file, project) } }
       ?.key?.id
   else null)

private fun webContextProximityFromProviders(kind: String,
                                             name: String,
                                             psiDir: PsiDirectory): CachedValueProvider.Result<Int?> {
  val dependencies = mutableSetOf<Any>()
  var proximity: Int? = null
  for (provider in WEB_SYMBOLS_CONTEXT_EP.allFor(kind, name)
    .plus(if (kind == KIND_FRAMEWORK) WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.allFor(name) else emptyList())
  ) {
    val result = provider.isEnabled(psiDir)
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

private const val emptyContext = "%EMPTY%"

private fun withContextChangeCheck(kind: String, psiDir: PsiDirectory, file: VirtualFile?, configInDir: ContextConfigInDir): ContextName? {
  val project = psiDir.project
  val currentState = findContextInDirOrFileCached(kind, psiDir, file, configInDir)

  val contextFile = file ?: psiDir.virtualFile
  val stateMap = project.getUserData(PREV_CONTEXT_KEY)
                 ?: (project as UserDataHolderEx).putUserDataIfAbsent(PREV_CONTEXT_KEY, ContainerUtil.createConcurrentWeakMap())
  val kindMap = stateMap.computeIfAbsent(contextFile) { ConcurrentHashMap() }
  val prevState = kindMap.put(kind, currentState ?: emptyContext)
  if (prevState != null && prevState != (currentState ?: emptyContext)) {
    reloadProject(kind, prevState.takeIf { it != emptyContext } ?: "none", currentState ?: "none", project, contextFile)
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

class WebSymbolsContextProjectRootsListener : ModuleRootListener {

  override fun rootsChanged(event: ModuleRootEvent) {
    event.project.putUserData(PREV_CONTEXT_KEY, null)
  }

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
        ProjectRootManagerEx.getInstanceEx(project)
          .makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
        project.putUserData(CONTEXT_RELOAD_MARKER_KEY, null)
      }
    }, Condition<Any> {
    project.disposed.value(null).also {
      // Clear the flag in case the project is recycled
      if (it) project.putUserData(CONTEXT_RELOAD_MARKER_KEY, null)
    }
  })
}