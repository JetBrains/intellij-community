// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.codeInsight.AnnotationsPanel
import com.intellij.java.coverage.JavaCoverageBundle
import com.intellij.openapi.options.UiDslUnnamedConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.PsiClass
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.selectedValueMatches
import com.intellij.util.ArrayUtil

private class JavaCoverageOptions(private val project: Project) : UiDslUnnamedConfigurable.Simple(), CoverageOptions {
  private val coverageOptionsProvider = JavaCoverageOptionsProvider.getInstance(project)

  override fun Panel.createContent() {
    group(JavaCoverageBundle.message("settings.coverage.java.java.coverage")) {
      lateinit var runner: ComboBox<CoverageRunner>
      row(JavaCoverageBundle.message("run.configuration.choose.coverage.runner")) {
        val runnerCell = comboBox(collectJavaRunners(), textListCellRenderer("", CoverageRunner::getPresentableName))
          .bindItem(coverageOptionsProvider::coverageRunner)
        runner = runnerCell.component
      }
      val isIdeaRunner = runner.selectedValueMatches { it is IDEACoverageRunner }
      row {
        checkBox(JavaCoverageBundle.message("run.configuration.coverage.branches"))
          .bindSelected(coverageOptionsProvider::branchCoverage)
          .comment(JavaCoverageBundle.message("run.configuration.coverage.branches.comment"))
          .visibleIf(runner.selectedValueMatches(CoverageRunner?::mayHaveBranchCoverage))
      }
      row {
        checkBox(JavaCoverageBundle.message("run.configuration.track.per.test.coverage"))
          .bindSelected(coverageOptionsProvider::testTracking)
          .comment(JavaCoverageBundle.message("run.configuration.track.per.test.coverage.comment"))
          .visibleIf(runner.selectedValueMatches { it != null && it.isCoverageByTestApplicable })
      }
      row {
        checkBox(JavaCoverageBundle.message("run.configuration.enable.coverage.in.test.folders"))
          .bindSelected(coverageOptionsProvider::testModulesCoverage)
      }
      row {
        checkBox(JavaCoverageBundle.message("settings.coverage.java.ignore.implicitly.declared.default.constructors"))
          .bindSelected(coverageOptionsProvider::ignoreImplicitConstructors)
          .visibleIf(isIdeaRunner)
      }
      row {
        val excludeAnnotationsPanel = object : AnnotationsPanel(
          project, "Exclude", coverageOptionsProvider.excludeAnnotationPatterns,
          JavaCoverageOptionsProvider.defaultExcludeAnnotationPatterns
        ) {
          override fun isAnnotationAccepted(annotation: PsiClass): Boolean {
            return annotation.containingClass == null
          }
        }

        cell(excludeAnnotationsPanel.component)
          .align(Align.FILL)
          .visibleIf(isIdeaRunner)
          .onIsModified {
            !excludeAnnotationsPanel.annotations.contentEquals(ArrayUtil.toStringArray(coverageOptionsProvider.excludeAnnotationPatterns))
          }.onApply {
            coverageOptionsProvider.excludeAnnotationPatterns = excludeAnnotationsPanel.annotations.asList()
          }.onReset {
            excludeAnnotationsPanel.resetAnnotations(coverageOptionsProvider.excludeAnnotationPatterns)
          }
      }
    }
  }
}

private fun collectJavaRunners(): List<CoverageRunner> {
  val javaEngine = JavaCoverageEngine.getInstance()
  return CoverageRunner.EP_NAME.extensionList.filter { it.acceptsCoverageEngine(javaEngine) }
}

/**
 * @return true iff coverage runner may have (but not always) branch coverage
 */
private fun CoverageRunner?.mayHaveBranchCoverage(): Boolean {
  if (this == null) return false
  if (this !is JavaCoverageRunner) return true
  val alwaysAvailable = isBranchInfoAvailable(false)
  val neverAvailable = !isBranchInfoAvailable(true)
  return !alwaysAvailable && !neverAvailable
}
