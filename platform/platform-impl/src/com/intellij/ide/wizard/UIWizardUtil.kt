// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIWizardUtil")

package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent
import javax.swing.JLabel


val WizardContext.projectOrDefault get() = project ?: ProjectManager.getInstance().defaultProject

fun getPresentablePath(path: String) = com.intellij.openapi.ui.getPresentablePath(path)

fun getCanonicalPath(path: String, removeLastSlash: Boolean = true) = com.intellij.openapi.ui.getCanonicalPath(path, removeLastSlash)

fun <T1, T2> T1.chain(f1: (T1) -> T2): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep {

  val s1 = f1(this)
  return stepSequence(this, s1)
}

fun <T1, T2, T3> T1.chain(f1: (T1) -> T2, f2: (T2) -> T3): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep, T3 : NewProjectWizardStep {

  val s1 = f1(this)
  val s2 = f2(s1)
  return stepSequence(this, s1, s2)
}

fun <T1, T2, T3, T4> T1.chain(f1: (T1) -> T2, f2: (T2) -> T3, f3: (T3) -> T4): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep, T3 : NewProjectWizardStep, T4 : NewProjectWizardStep {

  val s1 = f1(this)
  val s2 = f2(s1)
  val s3 = f3(s2)
  return stepSequence(this, s1, s2, s3)
}

fun <T1, T2, T3, T4, T5> T1.chain(f1: (T1) -> T2, f2: (T2) -> T3, f3: (T3) -> T4, f4: (T4) -> T5): NewProjectWizardStep
  where T1 : NewProjectWizardStep, T2 : NewProjectWizardStep, T3 : NewProjectWizardStep, T4 : NewProjectWizardStep, T5 : NewProjectWizardStep {

  val s1 = f1(this)
  val s2 = f2(s1)
  val s3 = f3(s2)
  val s4 = f4(s3)
  return stepSequence(this, s1, s2, s3, s4)
}

fun stepSequence(first: NewProjectWizardStep, vararg rest: NewProjectWizardStep): NewProjectWizardStep {
  val steps = listOf(first) + rest
  return object : AbstractNewProjectWizardStep(first) {
    override fun setupUI(builder: Panel) {
      for (step in steps) {
        step.setupUI(builder)
      }
    }

    override fun setupProject(project: Project) {
      for (step in steps) {
        step.setupProject(project)
      }
    }
  }
}

fun DialogPanel.setMinimumWidthForAllRowLabels(width: Int) {
  UIUtil.uiTraverser(this).asSequence()
    .filterIsInstance<JLabel>()
    .filter { isRowLabel(it) }
    .forEach { it.setMinimumWidth(width) }
}

private fun isRowLabel(label: JLabel): Boolean {
  val layout = (label.parent as? DialogPanel)?.layout as? GridLayout
  if (layout == null) {
    return false
  }
  val constraints = layout.getConstraints(label)
  return label.getClientProperty(DslComponentProperty.ROW_LABEL) == true && constraints != null && constraints.gaps.left == 0
}

private fun JComponent.setMinimumWidth(width: Int) {
  minimumSize = minimumSize.apply { this.width = width }
}