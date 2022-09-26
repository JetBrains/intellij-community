// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.framework.impl

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
import com.intellij.webSymbols.WebSymbolsRegistryManager
import com.intellij.webSymbols.framework.DependencyProximityProvider
import com.intellij.webSymbols.framework.DependencyProximityProvider.Companion.mergeProximity
import com.intellij.webSymbols.framework.DependencyProximityProvider.DependenciesKind
import com.intellij.webSymbols.framework.WebFramework
import com.intellij.webSymbols.framework.WebFrameworkContext
import com.intellij.webSymbols.framework.WebFrameworkContext.Companion.WEB_FRAMEWORK_CONTEXT_EP
import com.intellij.webSymbols.framework.WebFrameworkContext.Companion.WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED
import com.intellij.webSymbols.framework.WebFrameworksConfiguration
import com.intellij.webSymbols.impl.WebSymbolsRegistryManagerImpl
import com.intellij.webSymbols.utils.findOriginalFile
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.Pair
import kotlin.collections.component1
import kotlin.collections.component2

private val CONTEXT_KEY: Key<MutableMap<String, CachedValue<Int?>>> = Key("web.isContext")
private val PREV_CONTEXT_KEY = Key<MutableMap<VirtualFile, Optional<String>>>("web.isContext.prev")
private val CONTEXT_RELOAD_MARKER_KEY = Key<Any>("web.isContext.reloadMarker")
private val reloadMonitor = Any()
private val LOG = Logger.getInstance(WebFrameworkContext::class.java)

internal fun findWebSymbolsFrameworkInContext(context: PsiElement): WebFramework? {
  if (!context.isValid) {
    return null
  }
  if (context is PsiDirectory) {
    return withContextChangeCheck(context, null)
  }
  val psiFile = InjectedLanguageManager.getInstance(context.project).getTopLevelFile(context) ?: return null
  findEnabledFromProviders(psiFile)?.let { return it }

  // TODO support script pattern

  val file = findOriginalFile(psiFile.originalFile.virtualFile)
  @Suppress("DEPRECATION")
  return findWebSymbolsFrameworkInContext(
    (if (file != null && file.isInLocalFileSystem) file
    else psiFile.project.baseDir) ?: return null,
    psiFile.project)
}

internal fun findWebSymbolsFrameworkInContext(context: VirtualFile, project: Project): WebFramework? {
  if (project.isDisposed) return null
  val dirContext = context.isDirectory
  val file: VirtualFile? = findOriginalFile(context)
  if (file?.isDirectory == false) {
    findEnabledFromProviders(file, project)
      ?.let { return it }
  }
  val psiDir = (if (dirContext) file else file?.parent)
                 ?.let { if (it.isValid) PsiManager.getInstance(project).findDirectory(it) else null }
               ?: return null
  return withContextChangeCheck(psiDir, file)
}

private fun findWebFrameworkInContextCached(directory: PsiDirectory, file: VirtualFile?): WebFramework? {
  val project = directory.project
  val configInDir = CachedValuesManager.getCachedValue(directory) {
    val result = loadFrameworkConfiguration(directory)
    CachedValueProvider.Result.create(result, result.dependencies)
  }

  file
    ?.let { configInDir.findByFileName(it) }
    ?.let { return it }
  val proximityPerFrameworkFromConfig = configInDir.proximityPerFramework

  val proximityPerFrameworkFromExtensionsMap = (directory.getUserData(CONTEXT_KEY)
                                                ?: (directory as UserDataHolderEx).putUserDataIfAbsent(CONTEXT_KEY, ConcurrentHashMap()))

  val proximityPerFrameworkFromExtensions = WebFramework.all.asSequence()
    .map { it.id }
    .map { framework ->
      Pair(framework,
           proximityPerFrameworkFromExtensionsMap.computeIfAbsent(framework) {
             CachedValuesManager.getManager(directory.project).createCachedValue {
               webContextProximityFromProviders(framework, directory)
             }
           }.value
      )
    }
    .filter { it.second != null }
    .toMap()

  return proximityPerFrameworkFromConfig.keys
    .asSequence().plus(proximityPerFrameworkFromExtensions.keys)
    .distinct()
    .map {
      val a = proximityPerFrameworkFromConfig[it]
      val b = proximityPerFrameworkFromExtensions[it]?.toDouble()
      Pair(it, if (a != null && b != null) a.coerceAtMost(b) else a ?: b)
    }
    .filter { file == null || !isForbiddenFromProviders(it.first, file, directory.project, configInDir.disableWhen[it.first]) }
    .minByOrNull { it.second!! }
    ?.first
    ?.takeIf { file == null || !isAnyForbidden(file, project) }
    ?.let { WebFramework.get(it) }
}

private fun calcProximityPerFrameworkFromRules(directory: PsiDirectory,
                                               enableWhen: Map<String, List<WebFrameworksConfiguration.EnablementRules>>): Pair<Map<String, Double>, Set<ModificationTracker>> {
  val result = mutableMapOf<String, Double>()
  val modificationTrackers = mutableSetOf<ModificationTracker>()

  // Check enabled IDE libraries
  val ideLibToFramework = enableWhen
    .flatMap { (key, value) -> value.asSequence().flatMap { it.ideLibraries }.map { Pair(it, key) } }
    .groupByTo(mutableMapOf(), { it.first }, { it.second })

  DependencyProximityProvider.calculateProximity(directory, ideLibToFramework.keys, DependenciesKind.IdeLibrary)
    .let {
      it.dependency2proximity.forEach { (lib, proximity) ->
        ideLibToFramework[lib]?.forEach { framework ->
          result.merge(framework, proximity, ::mergeProximity)
        }
      }
      modificationTrackers.addAll(it.modificationTrackers)
    }

  // Check packages by `package.json` entries
  val depToFramework = enableWhen
    .flatMap { (key, value) -> value.asSequence().flatMap { it.pkgManagerDependencies }.map { Pair(it, key) } }
    .groupBy({ it.first }, { it.second })

  DependencyProximityProvider.calculateProximity(directory, depToFramework.keys, DependenciesKind.PackageManagerDependency)
    .let {
      it.dependency2proximity.forEach { (lib, proximity) ->
        depToFramework[lib]?.forEach { framework ->
          result.merge(framework, proximity, ::mergeProximity)
        }
      }
      modificationTrackers.addAll(it.modificationTrackers)
    }
  return Pair(result, modificationTrackers)
}

private fun loadFrameworkConfiguration(directory: PsiDirectory): FrameworkConfigInDir {
  val registryManager = WebSymbolsRegistryManager.getInstance(directory.project) as WebSymbolsRegistryManagerImpl
  val (configurations, tracker) = registryManager.getFrameworkConfigurations(directory)

  val enableWhen = configurations
    .flatMap { config ->
      config.enableWhen.entries.asSequence()
        .flatMap { (framework, rules) -> rules.asSequence().map { Pair(framework, it) } }
    }
    .groupBy({ it.first }, { it.second })
  val disableWhen = configurations
    .flatMap { config ->
      config.disableWhen.entries.asSequence()
        .flatMap { (framework, rules) -> rules.asSequence().map { Pair(framework, it) } }
    }
    .groupBy({ it.first }, { it.second })
  return FrameworkConfigInDir(directory, enableWhen, disableWhen, listOf(tracker))
}

private class FrameworkConfigInDir(val directory: PsiDirectory,
                                   val enableWhen: Map<String, List<WebFrameworksConfiguration.EnablementRules>>,
                                   val disableWhen: Map<String, List<WebFrameworksConfiguration.DisablementRules>>,
                                   val dependencies: List<Any>) {

  private val frameworkByFile = ConcurrentHashMap<String, Any>()

  private val proximityCache = CachedValuesManager.getManager(directory.project).createCachedValue {
    val result = calcProximityPerFrameworkFromRules(directory, enableWhen)
    CachedValueProvider.Result.create(result.first, dependencies + result.second)
  }

  val proximityPerFramework: Map<String, Double> get() = proximityCache.value

  fun findByFileName(file: VirtualFile): WebFramework? =
    frameworkByFile.computeIfAbsent(file.name) { fileName ->
      enableWhen.keys
        .find { framework ->
          enableWhen[framework]?.any {
            matchFileExt(fileName, it.fileExtensions) || matchFileName(fileName, it.fileNamePatterns)
          } == true
          && disableWhen[framework]?.any {
            matchFileExt(fileName, it.fileExtensions) || matchFileName(fileName, it.fileNamePatterns)
          } != true
        }
        ?.let {
          WebFramework.get(it)
        } ?: ""
    } as? WebFramework

}

private fun isForbiddenFromProviders(framework: String,
                                     file: VirtualFile,
                                     project: Project,
                                     disableWhen: List<WebFrameworksConfiguration.DisablementRules>?): Boolean =
  WEB_FRAMEWORK_CONTEXT_EP.allFor(framework).plus(WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.allFor(framework))
    .any { it.isForbidden(file, project) }
  || disableWhen?.any { matchFileName(file.name, it.fileNamePatterns) || matchFileExt(file.name, it.fileExtensions) } == true

private fun isAnyForbidden(context: VirtualFile, project: Project): Boolean =
  WEB_FRAMEWORK_CONTEXT_EP.forAny().plus(WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.forAny()).any { it.isForbidden(context, project) }

private fun findEnabledFromProviders(psiFile: PsiFile): WebFramework? =
  WEB_FRAMEWORK_CONTEXT_EP.all.asSequence().plus(WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.all.asSequence())
    .filter { extension -> extension.value.any { it.isEnabled(psiFile) } }.firstOrNull()?.key

private fun findEnabledFromProviders(file: VirtualFile, project: Project): WebFramework? =
  WEB_FRAMEWORK_CONTEXT_EP.all.asSequence().plus(WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.all.asSequence())
    .filter { extension -> extension.value.any { it.isEnabled(file, project) } }.firstOrNull()?.key

private fun webContextProximityFromProviders(framework: String,
                                             psiDir: PsiDirectory): CachedValueProvider.Result<Int?> {
  val dependencies = mutableSetOf<Any>()
  var proximity: Int? = null
  for (provider in WEB_FRAMEWORK_CONTEXT_EP.allFor(framework).plus(WEB_FRAMEWORK_CONTEXT_EP_DEPRECATED.allFor(framework))) {
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

private fun withContextChangeCheck(psiDir: PsiDirectory, file: VirtualFile?): WebFramework? {
  val project = psiDir.project
  val currentState = findWebFrameworkInContextCached(psiDir, file)

  val contextFile = file ?: psiDir.virtualFile
  val stateMap = project.getUserData(PREV_CONTEXT_KEY)
                 ?: (project as UserDataHolderEx).putUserDataIfAbsent(PREV_CONTEXT_KEY, ContainerUtil.createConcurrentWeakMap())
  val prevState = stateMap.put(contextFile, Optional.ofNullable(currentState?.id))
  if (prevState != null && prevState.orElse(null) != currentState?.id) {
    reloadProject(prevState.orElse("none"), currentState?.id ?: "none", project, contextFile)
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

class WebFrameworkContextProjectRootsListener : ModuleRootListener {

  override fun rootsChanged(event: ModuleRootEvent) {
    event.project.putUserData(PREV_CONTEXT_KEY, null)
  }

}

private fun reloadProject(prevState: String, newState: String, project: Project, file: VirtualFile) {
  synchronized(reloadMonitor) {
    if (project.getUserData(CONTEXT_RELOAD_MARKER_KEY) != null) {
      return
    }
    project.putUserData(CONTEXT_RELOAD_MARKER_KEY, true)
  }
  LOG.info("Reloading project ${project.name} on Web framework (${prevState} -> ${newState}) context change in file ${file.path}.")
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