// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

/**
 * Vertical step in new project wizard.
 * Represents small part of UI [setupUI] and rules how this UI applies [setupProject] on new project.
 * All steps form tree of steps that applies in order from root to leaf.
 *
 * @see NewProjectWizardMultiStepFactory
 */
interface NewProjectWizardStep {

  /**
   * New project wizard context that is used to configure main properties of project. Project name, location, SDK, etc.
   */
  val context: WizardContext

  /**
   * Graph to add dependencies between UI properties.
   * Expected that root step defines [propertyGraph] for other children steps.
   * So the vast majority of consumers shouldn't do it, and get [propertyGraph] from local property of this class.
   */
  val propertyGraph: PropertyGraph

  /**
   * Setups UI using Kotlin DSL. Use [context] to get [propertyGraph] or UI properties from parent steps.
   * ```
   * override fun setupUI(builder: Panel) {
   *   with(builder) {
   *     ...UI definitions...
   *   }
   * }
   * ```
   * See also: `https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl.html`
   */
  fun setupUI(builder: Panel)

  /**
   * Applies data from UI into project model or settings.
   */
  fun setupProject(project: Project)

  /**
   * Factory for root new project wizard step.
   * It is needed to initialize root step's context, property graph and
   * other wizard data that needed to configure and create new project.
   * @see ChildStepFactory
   */
  interface RootStepFactory {

    fun createStep(context: WizardContext): NewProjectWizardStep
  }

  /**
   * Factory for new project wizard step with parent step.
   * It is needed to transfer data from parents into children steps.
   */
  interface ChildStepFactory<P : NewProjectWizardStep> {

    fun createStep(parent: P): NewProjectWizardStep
  }
}