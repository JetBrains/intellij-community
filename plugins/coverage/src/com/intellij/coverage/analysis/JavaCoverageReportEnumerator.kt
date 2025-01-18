// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis

import com.intellij.coverage.CoverageSuitesBundle
import com.intellij.coverage.analysis.PackageAnnotator.PackageCoverageInfo
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile

internal object JavaCoverageReportEnumerator {
  @JvmStatic
  fun collectSummaryInReport(bundle: CoverageSuitesBundle, project: Project, collector: CoverageInfoCollector) {
    val projectData = bundle.coverageData ?: return
    val packageAnnotator = PackageAnnotator(bundle, project, projectData)

    val flattenPackages = hashMapOf<String, PackageCoverageInfo>()
    val flattenDirectories = hashMapOf<VirtualFile, PackageCoverageInfo>()

    projectData.classesCollection.map { it.name }.groupBy { fqn ->
      val vmName = AnalysisUtils.fqnToInternalName(fqn)
      AnalysisUtils.getSourceToplevelFQName(vmName)
    }.mapValues { (_, names) -> names.map(StringUtil::getShortName).associateWith { null } }
      .forEach { (topLevelName, simpleNames) ->
        val packageVMName = AnalysisUtils.fqnToInternalName(StringUtil.getPackageName(topLevelName))
        val result = packageAnnotator.visitFiles(topLevelName, simpleNames, packageVMName) ?: return@forEach
        collector.addClass(topLevelName, result.info)
        flattenPackages.getOrPut(AnalysisUtils.internalNameToFqn(packageVMName)) { PackageCoverageInfo() }.append(result.info)
        flattenDirectories.getOrPut(result.directory) { PackageCoverageInfo() }.append(result.info)
      }

    JavaCoverageClassesAnnotator.annotatePackages(flattenPackages, collector)
    JavaCoverageClassesAnnotator.annotateDirectories(flattenDirectories, collector, collectSourceRoots(project))
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
}