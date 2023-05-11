// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.xml

import com.intellij.coverage.*
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.coverage.view.JavaCoverageViewExtension
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.java.coverage.JavaCoverageBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class XMLReportEngine : CoverageEngine() {
  override fun createCoverageSuite(covRunner: CoverageRunner,
                                   name: String,
                                   coverageDataFileProvider: CoverageFileProvider,
                                   filters: Array<out String>?,
                                   lastCoverageTimeStamp: Long,
                                   suiteToMerge: String?,
                                   coverageByTestEnabled: Boolean,
                                   tracingEnabled: Boolean,
                                   trackTestFolders: Boolean,
                                   project: Project?): CoverageSuite? {
    if (covRunner !is XMLReportRunner) return null
    return XMLReportSuite(this, name, coverageDataFileProvider, lastCoverageTimeStamp, trackTestFolders, covRunner, project)
  }

  override fun createCoverageSuite(covRunner: CoverageRunner,
                                   name: String,
                                   coverageDataFileProvider: CoverageFileProvider,
                                   config: CoverageEnabledConfiguration) = error("Should not be called")

  override fun createEmptyCoverageSuite(coverageRunner: CoverageRunner): XMLReportSuite? {
    if (coverageRunner !is XMLReportRunner) return null
    return XMLReportSuite(this, coverageRunner)
  }

  override fun coverageEditorHighlightingApplicableTo(psiFile: PsiFile): Boolean {
    val suite = CoverageDataManager.getInstance(psiFile.project).currentSuitesBundle ?: return false
    return acceptedByFilters(psiFile, suite)
  }

  override fun acceptedByFilters(psiFile: PsiFile, suite: CoverageSuitesBundle): Boolean {
    val (packageName, fileName) = psiFile.packageAndFileName() ?: return false
    for (xmlSuite in suite.suites) {
      if (xmlSuite !is XMLReportSuite) continue
      if (xmlSuite.getFileInfo(packageName, fileName) != null) return true
    }
    return false
  }

  override fun createCoverageViewExtension(project: Project,
                                           suiteBundle: CoverageSuitesBundle?,
                                           stateBean: CoverageViewManager.StateBean?) =
    object : JavaCoverageViewExtension(getCoverageAnnotator(project), project, suiteBundle, stateBean) {
      override fun isBranchInfoAvailable(coverageRunner: CoverageRunner?, branchCoverage: Boolean) = true
    }

  override fun createSrcFileAnnotator(file: PsiFile?, editor: Editor?) = XMLReportEditorAnnotator(file, editor)
  override fun isApplicableTo(conf: RunConfigurationBase<*>) = false
  override fun canHavePerTestCoverage(conf: RunConfigurationBase<*>) = false
  override fun getPresentableText() = JavaCoverageBundle.message("coverage.xml.report.title")
  override fun getCoverageAnnotator(project: Project) = XMLReportAnnotator.getInstance(project)
  override fun recompileProjectAndRerunAction(module: Module, suite: CoverageSuitesBundle, chooseSuiteAction: Runnable) = false
  override fun getQualifiedNames(sourceFile: PsiFile) = error("Should not be called")
  override fun findTestsByNames(testNames: Array<out String>, project: Project) = error("Should not be called")
  override fun getTestMethodName(element: PsiElement, testProxy: AbstractTestProxy) = error("Should not be called")
  override fun createCoverageEnabledConfiguration(conf: RunConfigurationBase<*>) = error("Should not be called")
}

internal fun PsiFile.packageAndFileName(): Pair<String, String>? {
  if (this !is PsiClassOwner) return null
  val packageName = runReadAction { packageName } ?: return null
  return packageName to name
}
