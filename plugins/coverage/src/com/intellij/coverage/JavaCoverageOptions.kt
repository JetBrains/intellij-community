// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage

import com.intellij.codeInsight.AnnotationsPanel
import com.intellij.java.coverage.JavaCoverageBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiClass
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ArrayUtil
import javax.swing.JComponent

class JavaCoverageOptions(private val project: Project) : CoverageOptions() {

  private val coverageOptionsProvider = JavaCoverageOptionsProvider.getInstance(project)
  private var panel: DialogPanel? = null

  override fun createComponent(): JComponent? {
    panel = panel {
      group(JavaCoverageBundle.message("settings.coverage.java.java.coverage")) {
        row {
          checkBox(JavaCoverageBundle.message("settings.coverage.java.ignore.implicitly.declared.default.constructors"))
            .bindSelected(coverageOptionsProvider::ignoreImplicitConstructors)
        }
        row {
          val excludeAnnotationsPanel = object : AnnotationsPanel(
            project, "Exclude", "",
            coverageOptionsProvider.excludeAnnotationPatterns,
            JavaCoverageOptionsProvider.defaultExcludeAnnotationPatterns, emptySet(), false, false
          ) {
            override fun isAnnotationAccepted(annotation: PsiClass): Boolean {
              return annotation.containingClass == null
            }
          }

          cell(excludeAnnotationsPanel.component)
            .align(Align.FILL)
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
    return panel
  }

  override fun isModified(): Boolean {
    return panel?.isModified() == true
  }

  override fun apply() {
    panel?.apply()
  }

  override fun reset() {
    panel?.reset()
  }

  override fun disposeUIResources() {
    panel = null
  }
}