// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions

import com.intellij.coverage.*
import com.intellij.coverage.CoverageLogger.logSuiteImport
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ExternalReportImportManager(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ExternalReportImportManager = project.service()
  }
  
  fun chooseAndOpenSuites() {
    val suites = chooseAndImportCoverageReportsFromDisc()
    if (suites.isEmpty()) return
    openSuites(suites, false)
  }

  fun openSuites(suites: List<CoverageSuite>, closeCurrentlyOpened: Boolean) {
    val suitesByEngine = suites.groupBy { it.coverageEngine }

    if (closeCurrentlyOpened) {
      closeBundlesThatAreNotChosen(suitesByEngine)
    }

    for (engineSuites in suitesByEngine.values) {
      val bundle = CoverageSuitesBundle(engineSuites.toTypedArray<CoverageSuite>())
      logSuiteImport(project, bundle)
      CoverageDataManager.getInstance(project).chooseSuitesBundle(bundle)
    }

    if (!suites.isEmpty()) {
      ExternalCoverageWatchManager.getInstance(project).addRootsToWatch(suites)
    }
  }

  fun chooseAndImportCoverageReportsFromDisc(): List<CoverageSuite> {
    return FileChooser.chooseFiles(object : FileChooserDescriptor(true, false, false, false, false, true) {
      override fun isFileSelectable(file: VirtualFile?): Boolean = file != null && getCoverageRunner(file) != null
    }, project, null)
             .mapNotNull { file ->
               val runner = getCoverageRunner(file) ?: return@mapNotNull null
               file to runner
             }.takeIf { it.isNotEmpty() }
             ?.also { list ->
               //ensure timestamp in vfs is updated
               VfsUtil.markDirtyAndRefresh(false, false, false, *list.map { it.first }.toTypedArray())
             }
             ?.mapNotNull { (virtualFile, runner) ->
               val file = VfsUtilCore.virtualToIoFile(virtualFile)
               CoverageDataManager.getInstance(project).addExternalCoverageSuite(file, runner)
             } ?: emptyList()
  }

  private fun closeBundlesThatAreNotChosen(suitesByEngine: Map<CoverageEngine, List<CoverageSuite>>) {
    val activeSuites = CoverageDataManager.getInstance(project).activeSuites()
    val activeEngines = activeSuites.map { it.coverageEngine }.toHashSet()
    activeEngines.removeAll(suitesByEngine.keys)

    for (bundle in activeSuites) {
      if (bundle.coverageEngine in activeEngines) {
        CoverageDataManager.getInstance(project).closeSuitesBundle(bundle)
      }
    }
  }
}


private fun getCoverageRunner(file: VirtualFile): CoverageRunner? {
  for (runner in CoverageRunner.EP_NAME.extensionList) {
    for (extension in runner.dataFileExtensions) {
      if (Comparing.strEqual(file.extension, extension) && runner.canBeLoaded(VfsUtilCore.virtualToIoFile(file))) return runner
    }
  }
  return null
}
