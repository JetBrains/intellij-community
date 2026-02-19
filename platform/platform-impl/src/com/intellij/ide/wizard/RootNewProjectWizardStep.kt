// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.UserDataHolderBase

/**
 * The root project wizard step initializing a data holder and other properties shared with all descendant steps in the wizard.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#root-step">
 *   New Project Wizard API: Root Steps (IntelliJ Platform Docs)</a>
 */
class RootNewProjectWizardStep(override val context: WizardContext) : NewProjectWizardStep {

  override val data: UserDataHolderBase = UserDataHolderBase()

  override val propertyGraph: PropertyGraph = PropertyGraph("New project wizard")

  override var keywords: NewProjectWizardStep.Keywords = NewProjectWizardStep.Keywords()
}