// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.xml

import com.intellij.coverage.CoverageDataManager
import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.analysis.AnalysisUtils
import com.intellij.coverage.analysis.JavaCoverageAnnotator
import com.intellij.coverage.analysis.PackageAnnotator.ClassCoverageInfo
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.rt.coverage.report.XMLProjectData

@Service(Service.Level.PROJECT)
class XMLReportAnnotator(project: Project?) : JavaCoverageAnnotator(project) {
  override fun createRenewRequest(suite: CoverageSuitesBundle, dataManager: CoverageDataManager) = Runnable {
    val annotator = JavaPackageAnnotator()

    val classCoverage = hashMapOf<String, ClassCoverageInfo>()
    val packageCoverage = hashMapOf<String, PackageCoverageInfo>()
    val flattenPackageCoverage = hashMapOf<String, PackageCoverageInfo>()
    val directoryCoverage = hashMapOf<VirtualFile, PackageCoverageInfo>()
    val sourceRoots = (dataManager.doInReadActionIfProjectOpen { ModuleManager.getInstance(suite.project).modules } ?: emptyArray())
      .flatMap {
        OrderEnumerator.orderEntries(it).withoutSdk().withoutLibraries().withoutDepModules().productionOnly().sourceRoots.toList()
      }

    for (xmlSuite in suite.suites) {
      if (xmlSuite !is XMLReportSuite) continue
      val xmlReport = xmlSuite.getReportData() ?: continue
      for (classInfo in xmlReport.classes) {
        val currentCoverage = classCoverage.getOrPut(classInfo.name) { ClassCoverageInfo() }
        val thisSuiteCoverage = getCoverageForClass(classInfo)

        // apply delta
        val coverage = thisSuiteCoverage - currentCoverage
        currentCoverage.append(coverage)

        var packageName = classInfo.name.removeLastPart()
        addDirCoverage(packageName, classInfo.fileName, coverage, directoryCoverage, sourceRoots)

        flattenPackageCoverage.getOrPut(packageName) { PackageCoverageInfo() }.append(coverage)
        while (true) {
          packageCoverage.getOrPut(packageName) { PackageCoverageInfo() }.append(coverage)
          if (packageName.isEmpty()) break
          packageName = packageName.removeLastPart()
        }
      }
    }

    // Include anonymous and internal classes to the containing class
    classCoverage.entries.groupBy { AnalysisUtils.getSourceToplevelFQName(it.key) }.forEach { (className, classes) ->
      val coverage = ClassCoverageInfo()
      classes.forEach { coverage.append(it.value) }
      annotator.annotateClass(className, coverage)
    }
    for ((packageName, coverage) in packageCoverage) {
      annotator.annotatePackage(packageName, coverage, false)
    }
    for ((packageName, coverage) in flattenPackageCoverage) {
      annotator.annotatePackage(packageName, coverage, true)
    }
    for ((file, coverage) in directoryCoverage) {
      annotator.annotateSourceDirectory(file, coverage)
    }
    dataManager.triggerPresentationUpdate()
  }

  private fun addDirCoverage(packageName: String,
                             fileName: String?,
                             coverage: ClassCoverageInfo,
                             directoryCoverage: MutableMap<VirtualFile, PackageCoverageInfo>,
                             sourceRoots: List<VirtualFile>) {
    if (fileName == null) return
    val path = XMLReportSuite.getPath(packageName, fileName)
    for (root in sourceRoots) {
      var file: VirtualFile? = root.findFileByRelativePath(path)
      if (file == null) continue
      file = file.parent
      while (file != null) {
        directoryCoverage.getOrPut(file) { PackageCoverageInfo() }.append(coverage)
        file = file.parent
      }
      break
    }
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

private fun String.removeLastPart(): String {
  val index = lastIndexOf('.')
  if (index < 0) return ""
  return substring(0, index)
}
