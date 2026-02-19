// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.dsl.builder.Panel

/**
 * Defines a vertical step in the new project wizard.
 * It is a step selected by controls on one wizard view.
 * NPW step defines the part of UI [setupUI] and rules how this UI applies [setupProject] to the new project or module.
 * All steps form the step tree, where [setupUI] and [setupProject] functions are applied in the order from root to leaf.
 * However, the [setupProject] function won't be called for hidden steps.
 *
 * Wizard type (NPW or NMW) can be determined by [context] property
 * [WizardContext.isCreatingNewProject].
 *
 * @see com.intellij.ide.wizard.NewProjectWizardChainStep
 * @see AbstractNewProjectWizardStep
 * @see AbstractNewProjectWizardMultiStep
 * @see NewProjectWizardMultiStepFactory
 * @see GeneratorNewProjectWizard
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#wizard-steps">
 *   New Project Wizard API: Wizard Steps (IntelliJ Platform Docs)</a>
 */
interface NewProjectWizardStep {

  /**
   * New project wizard context that is used to configure the project's common properties.
   * Project name, location, SDK, etc.
   */
  val context: WizardContext

  /**
   * Graph to add dependencies between UI properties.
   * Expected that root step defines [propertyGraph] for child steps.
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
   * Step, which sets up specific step data, should put their data into [data] holder.
   * The step data should be extracted into interface where present data properties and
   * a static property that get step's data from [data] holder.
   *
   * Implementing data interface by the child step allows getting data directly from a parent step without dynamic casting data from [data] holder.
   *
   * @see NewProjectWizardBaseData
   */
  val data: UserDataHolder

  /**
   * Sets up UI using Kotlin DSL.
   *
   * Wizard type (NPW or NMW) can be determined by [context] property
   * [WizardContext.isCreatingNewProject].
   *
   * Style suggestions: If you need to create an abstract step with
   * common UI then create small protected UI segments like
   * `setupJdkUi` and `setupSampleCodeUi` and reuse them in `setupUi`
   * for each implementation. Don't override `setupUi` in abstract
   * steps, because later UI customization with abstract `setupUi` is
   * hard task.
   *
   * See also: `https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl.html`
   */
  fun setupUI(builder: Panel) {}

  /**
   * Applies data from UI into the project model or settings.
   * This function will be executed in both cases: new project and module wizards.
   *
   * Wizard type (NPW or NMW) can be determined by [context] property
   * [WizardContext.isCreatingNewProject].
   */
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

  companion object {

    const val GIT_PROPERTY_NAME: String = "NewProjectWizard.gitState"

    const val ADD_SAMPLE_CODE_PROPERTY_NAME: String = "NewProjectWizard.addSampleCodeState"

    const val GROUP_ID_PROPERTY_NAME: String = "NewProjectWizard.groupIdState"

    const val GENERATE_ONBOARDING_TIPS_NAME: String = "NewProjectWizard.generateOnboardingTips"

    @Deprecated("Use UIWizardUtil#setupProjectFromBuilder for creating modules from the legacy project builder.")
    val MODIFIABLE_MODULE_MODEL_KEY: Key<ModifiableModuleModel> = AbstractNewProjectWizardBuilder.MODIFIABLE_MODULE_MODEL_KEY
  }
}