// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.JavaCoverageEngineExtension
import com.intellij.coverage.JavaCoverageSuite
import com.intellij.coverage.analysis.PackageAnnotator.ClassCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.DirCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.ProjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = Logger.getInstance(JavaCoverageSummaryBuilder::class.java)

@ApiStatus.Internal
object JavaCoverageSummaryBuilder {
  suspend fun build(suite: CoverageSuitesBundle, project: Project, collector: CoverageInfoCollector) {
    if (suite.suites.all { it is JavaCoverageSuite && it.isSkipUnloadedClassesAnalysis }) {
      buildFromProjectData(suite, project, collector)
    }
    else {
      buildWithUnloadedClassesCollection(suite, project, collector)
    }
  }

  private suspend fun buildFromProjectData(bundle: CoverageSuitesBundle, project: Project, collector: CoverageInfoCollector) {
    val projectData = bundle.coverageData ?: return

    val flattenPackages = hashMapOf<String, PackageCoverageInfo>()
    val flattenDirectories = hashMapOf<VirtualFile, PackageCoverageInfo>()
    val searchScope = bundle.getSearchScope(project)

    val classes = projectData.classesCollection.map { it.name }.groupBy { fqn ->
      val vmName = AnalysisUtils.fqnToInternalName(fqn)
      AnalysisUtils.getSourceToplevelFQName(vmName)
    }.mapValues { (_, names) -> names.map(StringUtil::getShortName) }
    PackageAnnotator(bundle, project, projectData).use { packageAnnotator ->
      for ((topLevelName, simpleNames) in classes) {
        val file = CoverageSourceResolver.findFile(project, searchScope, topLevelName) ?: continue
        val packageVMName = AnalysisUtils.fqnToInternalName(StringUtil.getPackageName(topLevelName))
        val info = packageAnnotator.visitFiles(simpleNames.associateWith { null }, packageVMName)
        collector.addClass(topLevelName, info, file)
        flattenPackages.getOrPut(AnalysisUtils.internalNameToFqn(packageVMName)) { PackageCoverageInfo() }.append(info)
        file.parent?.let { directory ->
          flattenDirectories.getOrPut(directory) { PackageCoverageInfo() }.append(info)
        }
      }
    }

    annotatePackages(flattenPackages, collector)
    annotateDirectories(flattenDirectories, collector, collectSourceRoots(project))
  }

  private suspend fun buildWithUnloadedClassesCollection(
    suite: CoverageSuitesBundle,
    project: Project,
    collector: CoverageInfoCollector,
  ) {
    val projectData = suite.coverageData ?: return
    val requests = collectOutputRoots(suite, project)
    val progress = CoverageProgress(requests.values.sumOf(List<RequestRoot>::size))
    val dispatcher = Dispatchers.IO.limitedParallelism(getWorkingThreads())

    val results = coroutineScope {
      requests.map { (moduleRequest, roots) ->
        async(dispatcher) {
          ModuleCoverageBuilder(suite, project, projectData).build(moduleRequest, roots, progress)
        }
      }.awaitAll()
    }

    collectCoverage(results, collector)
  }

  private fun collectSourceRoots(project: Project): Set<VirtualFile> {
    val result = hashSetOf<VirtualFile>()
    for (module in ModuleManager.getInstance(project).modules) {
      val contentEntries = ModuleRootManager.getInstance(module).getContentEntries()
      for (contentEntry in contentEntries) {
        for (folder in contentEntry.getSourceFolders()) {
          val file = folder.getFile() ?: continue
          result.add(file)
        }
      }
    }
    return result
  }

  private fun collectCoverage(results: List<ModuleCoverageResult>, collector: CoverageInfoCollector) {
    val flattenPackages = HashMap<String, PackageCoverageInfo>()
    for ((classes, modulePackages, directories, sourceRoots) in results) {
      for ((className, collectedClass) in classes) {
        collector.addClass(className, collectedClass.info, collectedClass.sourceFile)
      }
      for ((packageVMName, info) in modulePackages) {
        val packageFQName = AnalysisUtils.internalNameToFqn(packageVMName)
        flattenPackages.getOrPut(packageFQName, ::PackageCoverageInfo).append(info)
      }
      annotateDirectories(directories, collector, sourceRoots)
    }
    annotatePackages(flattenPackages, collector)
  }

  /**
   * Collect coverage stats for all packages, based on flatten packages coverage.
   *
   * @param flattenPackages fqn to package coverage mapping
   */
  @JvmStatic
  fun annotatePackages(flattenPackages: Map<String, PackageCoverageInfo>, collector: CoverageInfoCollector) {
    val packages = HashMap<String, PackageCoverageInfo>()
    for ((flattenPackageFQName, info) in flattenPackages) {
      collector.addPackage(flattenPackageFQName, info, true)

      var packageFQName = flattenPackageFQName
      while (packageFQName.isNotEmpty()) {
        packages.getOrPut(packageFQName, ::PackageCoverageInfo).append(info)
        val index = packageFQName.lastIndexOf('.')
        if (index < 0) break
        packageFQName = packageFQName.substring(0, index)
      }
      packages.getOrPut("", ::PackageCoverageInfo).append(info)
    }
    for ((packageFQName, info) in packages) {
      collector.addPackage(packageFQName, info, false)
    }
  }

  /**
   * Collect coverage stats for all directories, based on flatten directories coverage.
   *
   * @param sourceRoots root directories where the calculation should stop
   */
  @JvmStatic
  fun annotateDirectories(
    flattenDirectories: Map<VirtualFile, PackageCoverageInfo>,
    collector: CoverageInfoCollector,
    sourceRoots: Set<VirtualFile>,
  ) {
    val directories = HashMap<VirtualFile, DirCoverageInfo>()
    for ((flattenDirectory, info) in flattenDirectories) {
      var directory: VirtualFile? = flattenDirectory
      while (directory != null) {
        directories.getOrPut(directory) { DirCoverageInfo(directory) }.append(info)
        if (directory in sourceRoots) break
        directory = directory.parent
      }
    }

    for (directory in directories.values) {
      collector.addSourceDirectory(directory.sourceRoot, directory)
    }
  }

  @JvmStatic
  fun getSourceRoots(module: Module): Set<VirtualFile> = getSourceFolders(module).mapNotNullTo(HashSet()) { it.file }

  private fun getWorkingThreads(): Int {
    val configuredThreads = Registry.intValue("idea.coverage.loading.threads")
    val maxThreads = Runtime.getRuntime().availableProcessors() - 1
    return (if (configuredThreads == 0) maxThreads else configuredThreads).coerceIn(1, maxThreads.coerceAtLeast(1))
  }
}

private fun getPackageRoots(module: Module, rootPackageVMName: String): Set<VirtualFile> {
  val result = HashSet<VirtualFile>()
  for (folder in getSourceFolders(module)) {
    val file = folder.file ?: continue
    val prefix = AnalysisUtils.fqnToInternalName(folder.packagePrefix)
    val relativeSrcRoot = file.findFileByRelativePath(rootPackageVMName.removePrefix(prefix)) ?: continue
    result.add(relativeSrcRoot)
  }
  return result
}

private fun getSourceFolders(module: Module) = ModuleRootManager.getInstance(module).contentEntries
  .flatMap { it.sourceFolders.asIterable() }
  .filterTo(HashSet()) { it.file != null }

private class ModuleCoverageBuilder(
  private val suite: CoverageSuitesBundle,
  private val project: Project,
  private val projectData: ProjectData,
) {
  private val classes = HashMap<String, CollectedClass>()
  private val flattenPackages = HashMap<String, PackageCoverageInfo>()
  private val flattenDirectories = HashMap<VirtualFile, PackageCoverageInfo>()

  suspend fun build(moduleRequest: ModuleRequest, roots: List<RequestRoot>, progress: CoverageProgress): ModuleCoverageResult {
    val module = moduleRequest.module
    val packageVMName = AnalysisUtils.fqnToInternalName(moduleRequest.packageName)
    PackageAnnotator(suite, project, projectData).use { classSummaryBuilder ->
      if (module.isDisposed) {
        LOG.warn("Module is already disposed: $module")
        progress.rootsVisited(roots.size)
        return ModuleCoverageResult(emptyMap(), emptyMap(), emptyMap(), emptySet())
      }

      val searchScope = GlobalSearchScope.moduleScope(module).intersectWith(suite.getSearchScope(project))
      for (locatedClass in locateClassFiles(packageVMName, roots, progress)) {
        collectClassCoverage(locatedClass, classSummaryBuilder, searchScope)
      }
      return ModuleCoverageResult(
        classes,
        flattenPackages,
        flattenDirectories,
        getPackageRoots(module, packageVMName),
      )
    }
  }

  private fun locateClassFiles(
    rootPackageVMName: String,
    roots: List<RequestRoot>,
    progress: CoverageProgress,
  ): List<LocatedClassFiles> {
    val requestsByRoot = roots.groupByTo(LinkedHashMap()) { RootRequestKey(it.root, it.packagePathInRoot) }
    return requestsByRoot.flatMap { (key, rootRequests) ->
      try {
        val requestedSimpleNames = if (rootRequests.any { it.simpleName == null }) null
        else rootRequests.mapNotNullTo(HashSet(), RequestRoot::simpleName)
        ClassFilesLocator.findClassFiles(key.root, rootPackageVMName, key.packagePathInRoot, requestedSimpleNames)
      }
      finally {
        progress.rootsVisited(rootRequests.size)
      }
    }
  }

  private suspend fun collectClassCoverage(
    locatedClass: LocatedClassFiles,
    classSummaryBuilder: PackageAnnotator,
    searchScope: GlobalSearchScope,
  ) {
    val (topLevelClassName, packageVMName, files) = locatedClass
    if (isClassExcluded(topLevelClassName)) return
    val children = files.asSequence()
      .filterNot(::ignoreClass)
      .associateBy(AnalysisUtils::getClassName)
    if (children.isEmpty()) return

    val info = classSummaryBuilder.visitFiles(children, packageVMName)
    val sourceFileName = classSummaryBuilder.getSourceFileName(children, packageVMName)
    val file = CoverageSourceResolver.findFile(project, searchScope, topLevelClassName, sourceFileName)
    if (file == null && children.values.none(::keepCoverageInfoForClassWithoutSource)) return

    classes[topLevelClassName] = CollectedClass(info, file)
    flattenPackages.getOrPut(packageVMName, ::PackageCoverageInfo).append(info)
    file?.parent?.let { directory ->
      flattenDirectories.getOrPut(directory, ::PackageCoverageInfo).append(info)
    }
  }

  private fun keepCoverageInfoForClassWithoutSource(classFile: Path): Boolean {
    return JavaCoverageEngineExtension.EP_NAME.extensionList.any { it.keepCoverageInfoForClassWithoutSource(suite, classFile) }
  }

  private fun ignoreClass(classFile: Path): Boolean {
    return JavaCoverageEngineExtension.EP_NAME.extensionList.any { it.ignoreCoverageForClass(suite, classFile) }
  }

  private fun isClassExcluded(fqn: String): Boolean {
    return suite.suites.all { it is JavaCoverageSuite && !it.isClassFiltered(fqn) }
  }
}

private data class RootRequestKey(val root: Path, val packagePathInRoot: String)

private data class CollectedClass(val info: ClassCoverageInfo, val sourceFile: VirtualFile?)

private data class ModuleCoverageResult(
  val classes: Map<String, CollectedClass>,
  val flattenPackages: Map<String, PackageCoverageInfo>,
  val flattenDirectories: Map<VirtualFile, PackageCoverageInfo>,
  val sourceRoots: Set<VirtualFile>,
)

private class CoverageProgress(private val rootsCount: Int) {
  private val indicator: ProgressIndicator? = ProgressIndicatorProvider.getGlobalProgressIndicator()
  private var visitedRoots = 0

  init {
    updateProgress()
  }

  @Synchronized
  fun rootsVisited(count: Int) {
    visitedRoots += count
    updateProgress()
  }

  private fun updateProgress() {
    if (rootsCount <= 1) return
    indicator?.isIndeterminate = false
    indicator?.fraction = visitedRoots / rootsCount.toDouble()
  }
}
