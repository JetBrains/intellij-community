// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*

/**
 * Vertical step in new project wizard.
 * Represents small part of UI [setupUI] and rules how this UI applies [setupProject] on new project.
 * All steps form list/tree of steps [NewProjectWizardMultiStep] that applies in order from root to leaf.
 *
 * @param context is used to configure main properties of project. Project name, location, SDK, etc.
 * Also, this context allows to share some configuration data by [Key] and [WizardContext.putUserData].
 * For example base project settings shared by [com.intellij.ide.wizard.NewProjectStep.Companion.KEY].
 * @param propertyGraph is graph to add dependencies between UI properties.
 * Expected that root step defines [propertyGraph] for other children steps.
 * So the vast majority of consumers shouldn't do it, and get [propertyGraph] from local property of this class.
 *
 * @see NewProjectWizardMultiStep
 * @see WizardContext
 * @see com.intellij.openapi.observable.properties.GraphProperty
 */
abstract class NewProjectWizardStep(
  protected val context: WizardContext,
  protected val propertyGraph: PropertyGraph
) {

  constructor(context: WizardContext) : this(context, PROPERTY_GRAPH_KEY.get(context))

  /**
   * Setups UI using Kotlin DSL. Use [context] to get [propertyGraph] or UI properties from parent steps.
   * ```
   * override fun setupUI(builder: RowBuilder) {
   *   with(builder) {
   *     ...UI definitions...
   *   }
   * }
   * ```
   * See also: `https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl.html`
   */
  abstract fun setupUI(builder: RowBuilder)

  /**
   * Applies data from UI into project model or settings.
   * Use [context] to get UI data from parent steps.
   */
  abstract fun setupProject(project: Project)

  init {
    PROPERTY_GRAPH_KEY.set(context, propertyGraph)
  }

  companion object {
    val PROPERTY_GRAPH_KEY = Key.create<PropertyGraph>(NewProjectWizardStep::class.java.name + "#" + PropertyGraph::class.java.name)
  }
}