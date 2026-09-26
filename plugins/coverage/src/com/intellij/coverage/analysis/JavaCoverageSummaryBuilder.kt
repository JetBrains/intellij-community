// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageBundle
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.JavaCoverageEngineExtension
import com.intellij.coverage.JavaCoverageRunner
import com.intellij.coverage.JavaCoverageSuite
import com.intellij.coverage.analysis.PackageAnnotator.ClassCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.DirCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.reportProgressScope
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.ProjectData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = Logger.getInstance(JavaCoverageSummaryBuilder::class.java)

@ApiStatus.Internal
object JavaCoverageSummaryBuilder {
  suspend fun build(
    suite: CoverageSuitesBundle,
    project: Project,
    collector: CoverageInfoCollector,
  ) {
    reportSequentialProgress(size = 2) { progress ->
      val projectData = progress.itemStep(CoverageBundle.message("coverage.view.load.report")) { suite.coverageData } ?: return
      progress.itemStep(CoverageBundle.message("coverage.view.build.statistics")) {
        if (suite.suites.all { it is JavaCoverageSuite && it.isSkipUnloadedClassesAnalysis }) {
          buildFromProjectData(suite, project, projectData, collector)
        }
        else {
          buildWithUnloadedClassesCollection(suite, project, projectData, collector)
        }
      }
    }
  }

  private suspend fun buildFromProjectData(
    bundle: CoverageSuitesBundle,
    project: Project,
    projectData: ProjectData,
    collector: CoverageInfoCollector,
  ) {
    val flattenPackages = hashMapOf<String, PackageCoverageInfo>()
    val flattenDirectories = hashMapOf<VirtualFile, PackageCoverageInfo>()
    val searchScope = bundle.getSearchScope(project)

    val classes = projectData.classesCollection.groupBy { classData ->
      val vmName = AnalysisUtils.fqnToInternalName(classData.name)
      AnalysisUtils.getSourceToplevelFQName(vmName)
    }
    reportProgressScope(classes.size) { progress ->
      for ((topLevelName, classData) in classes) {
        progress.itemStep {
          val fileName = classData.firstNotNullOfOrNull { it.source }
          val file = CoverageSourceResolver.findFile(project, searchScope, topLevelName, fileName) ?: return@itemStep
          val packageVMName = AnalysisUtils.fqnToInternalName(StringUtil.getPackageName(topLevelName))
          val info = ClassCoverageInfo()
          for (data in classData) {
            PackageAnnotator.getSummaryInfo(data)?.let(info::append)
          }
          collector.addClass(topLevelName, info, file)
          flattenPackages.getOrPut(AnalysisUtils.internalNameToFqn(packageVMName)) { PackageCoverageInfo() }.append(info)
          file.parent?.let { directory ->
            flattenDirectories.getOrPut(directory) { PackageCoverageInfo() }.append(info)
          }
        }
      }
    }

    annotatePackages(flattenPackages, collector)
    annotateDirectories(flattenDirectories, collector, collectSourceRoots(project))
  }

  private suspend fun buildWithUnloadedClassesCollection(
    suite: CoverageSuitesBundle,
    project: Project,
    projectData: ProjectData,
    collector: CoverageInfoCollector,
  ) {
    val requests = collectOutputRoots(suite, project)
    val dispatcher = Dispatchers.IO.limitedParallelism(getWorkingThreads())

    val results = reportProgressScope(requests.size) { progress ->
      requests.map { moduleRequest ->
        async(dispatcher) {
          progress.itemStep {
            ModuleCoverageBuilder(suite, project, projectData, moduleRequest).build()
          }
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

}

internal fun getWorkingThreads(): Int {
  val configuredThreads = Registry.intValue("idea.coverage.loading.threads")
  val maxThreads = Runtime.getRuntime().availableProcessors() - 1
  return (if (configuredThreads == 0) maxThreads else configuredThreads).coerceIn(1, maxThreads.coerceAtLeast(1))
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
  private val moduleRequest: ModuleRequest,
) {
  private val classes = HashMap<String, CollectedClass>()
  private val pendingClasses = HashMap<String, PendingClassCoverage>()
  private val flattenPackages = HashMap<String, PackageCoverageInfo>()
  private val flattenDirectories = HashMap<VirtualFile, PackageCoverageInfo>()
  private val packageAnnotator = PackageAnnotator(projectData)

  suspend fun build(): ModuleCoverageResult {
    val module = moduleRequest.module
    if (module.isDisposed) {
      LOG.warn("Module is already disposed: $module")
      return ModuleCoverageResult(emptyMap(), emptyMap(), emptyMap(), emptySet())
    }

    val searchScope = GlobalSearchScope.moduleScope(module).intersectWith(suite.getSearchScope(project))
    val sourceRoots = HashSet<VirtualFile>()
    for ((packageName, _) in moduleRequest.packages) {
      val packageVMName = AnalysisUtils.fqnToInternalName(packageName)
      sourceRoots.addAll(getPackageRoots(module, packageVMName))
    }

    ClassFilesLocator.findClassFiles(moduleRequest.root, moduleRequest.packages).use { classFiles ->
      for (classFile in classFiles) {
        collectClassCoverage(classFile)
      }
    }
    finalizeCollectedClasses(searchScope)
    return ModuleCoverageResult(
      classes,
      flattenPackages,
      flattenDirectories,
      sourceRoots,
    )
  }

  private fun collectClassCoverage(classFile: LocatedClassFile) {
    val classVMName = AnalysisUtils.buildVMName(classFile.packageVMName, classFile.simpleName)
    val topLevelClassName = AnalysisUtils.getSourceToplevelFQName(classVMName)
    if (isClassExcluded(topLevelClassName) || ignoreClass(classFile.path)) return

    val className = AnalysisUtils.internalNameToFqn(classVMName)
    val pending = pendingClasses.getOrPut(topLevelClassName) { PendingClassCoverage(classFile.packageVMName) }
    val classData = suite.suites.mapNotNull { it.runner as? JavaCoverageRunner }.firstNotNullOfOrNull {
      it.getOrLoadCoverage(project,
                           projectData,
                           packageAnnotator::getUnloadedClassesProjectData,
                           className,
                           suite.isBranchCoverage(),
                           classFile::loadBytes)
    } ?: projectData.getClassData(className)
    PackageAnnotator.getSummaryInfo(classData)?.let(pending.info::append)
    if (pending.sourceFileName == null) {
      pending.sourceFileName = packageAnnotator.getSourceFileName(className) { classFile.loadBytes() }
    }
    if (!pending.keepWithoutSource) {
      pending.keepWithoutSource = keepCoverageInfoForClassWithoutSource(classFile.path)
    }
  }

  private suspend fun finalizeCollectedClasses(searchScope: GlobalSearchScope) {
    for ((topLevelClassName, pending) in pendingClasses) {
      val file = CoverageSourceResolver.findFile(project, searchScope, topLevelClassName, pending.sourceFileName)
      if (file == null && !pending.keepWithoutSource) continue

      classes[topLevelClassName] = CollectedClass(pending.info, file)
      flattenPackages.getOrPut(pending.packageVMName, ::PackageCoverageInfo).append(pending.info)
      file?.parent?.let { directory ->
        flattenDirectories.getOrPut(directory, ::PackageCoverageInfo).append(pending.info)
      }
    }
  }

  private fun keepCoverageInfoForClassWithoutSource(classFile: Path): Boolean {
    return JavaCoverageEngineExtension.EP_NAME.findFirstSafe { it.keepCoverageInfoForClassWithoutSource(suite, classFile) } != null
  }

  private fun ignoreClass(classFile: Path): Boolean {
    return JavaCoverageEngineExtension.EP_NAME.findFirstSafe { it.ignoreCoverageForClass(suite, classFile) } != null
  }

  private fun isClassExcluded(fqn: String): Boolean {
    return suite.suites.all { it is JavaCoverageSuite && !it.isClassFiltered(fqn) }
  }
}

private data class CollectedClass(val info: ClassCoverageInfo, val sourceFile: VirtualFile?)

private class PendingClassCoverage(val packageVMName: String) {
  val info = ClassCoverageInfo()
  var sourceFileName: String? = null
  var keepWithoutSource = false
}

private data class ModuleCoverageResult(
  val classes: Map<String, CollectedClass>,
  val flattenPackages: Map<String, PackageCoverageInfo>,
  val flattenDirectories: Map<VirtualFile, PackageCoverageInfo>,
  val sourceRoots: Set<VirtualFile>,
)
