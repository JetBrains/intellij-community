// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UIWizardUtil")

package com.intellij.ide.wizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.dsl.builder.Panel
import java.io.File


fun getPresentablePath(path: String): String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

fun getCanonicalPath(path: String, removeLastSlash: Boolean = true): String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}

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