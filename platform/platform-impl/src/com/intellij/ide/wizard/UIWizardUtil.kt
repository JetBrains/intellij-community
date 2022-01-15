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

fun <P : NewProjectWizardStep, C : NewProjectWizardStep> P.chain(factory: (P) -> C) = chainSteps(this, factory)

fun <P : NewProjectWizardStep, C : NewProjectWizardStep> chainSteps(parent: P, factory: (P) -> C): NewProjectWizardStep {
  val child = factory(parent)
  return object : AbstractNewProjectWizardStep(parent) {
    override fun setupUI(builder: Panel) {
      parent.setupUI(builder)
      child.setupUI(builder)
    }

    override fun setupProject(project: Project) {
      parent.setupProject(project)
      child.setupProject(project)
    }
  }
}