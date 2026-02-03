// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.xml

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.analysis.AnalysisUtils
import com.intellij.coverage.analysis.CoverageInfoCollector
import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.analysis.JavaCoverageClassesAnnotator
import com.intellij.coverage.analysis.PackageAnnotator.ClassCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.coverage.view.CoverageClassStructure
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.coverage.report.XMLProjectData

@Service(Service.Level.PROJECT)
class XMLReportAnnotator(project: Project?) : JavaCoverageAnnotator(project) {
  override fun createRenewRequest(suite: CoverageSuitesBundle, dataManager: CoverageDataManager) = Runnable {
    annotate(suite, dataManager, JavaCoverageInfoCollector(this))
    myStructure = CoverageClassStructure(project, this, suite)
    Disposer.register(this, myStructure)
    dataManager.triggerPresentationUpdate()
  }

  fun annotate(suite: CoverageSuitesBundle, dataManager: CoverageDataManager, collector: CoverageInfoCollector) {
    val classCoverage = hashMapOf<String, ClassCoverageInfo>()
    val flattenPackageCoverage = hashMapOf<String, PackageCoverageInfo>()
    val flattenDirectoryCoverage = hashMapOf<VirtualFile, PackageCoverageInfo>()
    val sourceRoots = (dataManager.doInReadActionIfProjectOpen { ModuleManager.getInstance(suite.project).modules } ?: emptyArray())
      .flatMap { JavaCoverageClassesAnnotator.getSourceRoots(it) }.toHashSet()

    for (xmlSuite in suite.suites) {
      if (xmlSuite !is XMLReportSuite) continue
      val xmlReport = xmlSuite.getReportData() ?: continue
      for (classInfo in xmlReport.classes) {
        val currentCoverage = classCoverage.getOrPut(classInfo.name) { ClassCoverageInfo() }
        val thisSuiteCoverage = getCoverageForClass(classInfo)

        // apply delta
        val coverage = thisSuiteCoverage - currentCoverage
        currentCoverage.append(coverage)

        val packageName = StringUtil.getPackageName(classInfo.name)
        val virtualFile = findFile(packageName, classInfo.fileName, sourceRoots)

        flattenPackageCoverage.getOrPut(packageName) { PackageCoverageInfo() }.append(coverage)
        if (virtualFile != null) {
          flattenDirectoryCoverage.getOrPut(virtualFile) { PackageCoverageInfo() }.append(coverage)
        }
      }
    }

    // Include anonymous and internal classes to the containing class
    classCoverage.entries.groupBy { AnalysisUtils.getSourceToplevelFQName(it.key) }.forEach { (className, classes) ->
      val coverage = ClassCoverageInfo()
      classes.forEach { coverage.append(it.value) }
      collector.addClass(className, coverage)
    }

    JavaCoverageClassesAnnotator.annotatePackages(flattenPackageCoverage, collector)
    JavaCoverageClassesAnnotator.annotateDirectories(flattenDirectoryCoverage, collector, sourceRoots)
  }

  private fun findFile(packageName: String, fileName: String?, sourceRoots: Collection<VirtualFile>): VirtualFile? {
    if (fileName == null) return null
    val path = XMLReportSuite.getPath(packageName, fileName)
    for (root in sourceRoots) {
      val file = root.findFileByRelativePath(path) ?: continue
      return file.parent
    }
    return null
  }

  private fun getCoverageForClass(classInfo: XMLProjectData.ClassInfo): ClassCoverageInfo {
    val coverage = ClassCoverageInfo()
    coverage.totalBranchCount = classInfo.coveredBranches + classInfo.missedBranches
    coverage.coveredBranchCount = classInfo.coveredBranches
    coverage.totalMethodCount = classInfo.coveredMethods + classInfo.missedMethods
    coverage.coveredMethodCount = classInfo.coveredMethods
    coverage.totalClassCount = if (coverage.totalMethodCount > 0) 1 else 0
    coverage.coveredClassCount = if (classInfo.coveredMethods > 0) 1 else 0
    coverage.totalLineCount = classInfo.coveredLines + classInfo.missedLines
    coverage.fullyCoveredLineCount = classInfo.coveredLines
    return coverage
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): XMLReportAnnotator {
      return project.getService(XMLReportAnnotator::class.java)
    }
  }

}

private operator fun ClassCoverageInfo.minus(other: ClassCoverageInfo): ClassCoverageInfo {
  val result = ClassCoverageInfo()
  result.totalLineCount = totalLineCount - other.totalLineCount
  result.fullyCoveredLineCount = fullyCoveredLineCount - other.fullyCoveredLineCount
  result.partiallyCoveredLineCount = partiallyCoveredLineCount - other.partiallyCoveredLineCount
  result.totalBranchCount = totalBranchCount - other.totalBranchCount
  result.coveredBranchCount = coveredBranchCount - other.coveredBranchCount
  result.totalMethodCount = totalMethodCount - other.totalMethodCount
  result.coveredMethodCount = coveredMethodCount - other.coveredMethodCount
  result.totalClassCount = totalClassCount - other.totalClassCount
  result.coveredClassCount = coveredClassCount - other.coveredClassCount
  return result
}
