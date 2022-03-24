// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.dsl.builder.Panel

/**
 * Vertical step in new project wizard.
 * Represents small part of UI [setupUI] and rules how this UI applies [setupProject] on new project.
 * All steps form tree of steps that applies in order from root to leaf.
 *
 * @see AbstractNewProjectWizardStep
 * @see AbstractNewProjectWizardMultiStep
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
   * The keywords that are used for search field input pattern matching
   */
  val keywords: Keywords

  /**
   * Data holder that needed to share step data.
   *
   * Convention:
   *
   * Step which setups specific step data, should put these data into [data] holder.
   * Also, step data should be extracted into interface where ara data properties and
   * static property to get data from [data] holder.
   * Usually our [NewProjectWizardStep]s implement their data interface.
   * It allows getting data directly from parent step without dynamic casting data from [data] holder.
   *
   * @see NewProjectWizardBaseData
   */
  val data: UserDataHolder

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
  @JvmDefault
  fun setupUI(builder: Panel) {}

  /**
   * Applies data from UI into project model or settings.
   */
  @JvmDefault
  fun setupProject(project: Project) {}

  /**
   * See related doc for [NewProjectWizardStep.keywords].
   */
  class Keywords {
    private val keywords = HashMap<Any, Set<String>>()

    fun toSet(): Set<String> {
      return keywords.values.flatten().toSet()
    }

    fun add(owner: Any, keywords: Iterable<String>) {
      this.keywords[owner] = keywords.toSet()
    }
  }
}