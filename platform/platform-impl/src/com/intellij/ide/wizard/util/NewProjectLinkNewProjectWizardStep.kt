// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard.util

import com.intellij.ide.wizard.NewModuleBuilder
import com.intellij.ide.wizard.NewProjectBuilder
import com.intellij.ide.wizard.NewProjectWizardStep
import org.jetbrains.annotations.Nls

abstract class NewProjectLinkNewProjectWizardStep(parent: NewProjectWizardStep) : LinkNewProjectWizardStep(parent) {

  private val builder = if (context.isCreatingNewProject) NewProjectBuilder() else NewModuleBuilder()

  override val builderId: String = builder.builderId!!

  override val comment: String by lazy { getComment(builder.presentableName) }

  abstract fun getComment(
    name: @Nls(capitalization = Nls.Capitalization.Title) String
  ): @Nls(capitalization = Nls.Capitalization.Sentence) String
}