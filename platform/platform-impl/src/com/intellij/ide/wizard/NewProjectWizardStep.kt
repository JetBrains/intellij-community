// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ProjectConfigurator
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.annotations.ApiStatus

/**
 * Defines vertical step in new project wizard. It is step which
 * selects by controls on one wizard view. NPW step is small part
 * of UI [setupUI] and rules how this UI applies [setupProject] on
 * new project or new module. All steps form tree of steps are
 * applying in order from root to leaf.
 *
 * Wizard type (NPW or NMW) can be determined by [context] property
 * [WizardContext.isCreatingNewProject].
 *
 * @see com.intellij.ide.wizard.NewProjectWizardChainStep
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
   * Setups UI using Kotlin DSL.
   *
   * Wizard type (NPW or NMW) can be determined by [context] property
   * [WizardContext.isCreatingNewProject].
   *
   * Style suggestions: If you need to create abstract step with
   * common UI then create small protected UI segments like
   * `setupJdkUi` and `setupSampleCodeUi` and reuse them in `setupUi`
   * for each implementation. Don't override `setupUi` in abstract
   * steps, because later UI customization for only one consumer is
   * hard with abstract `setupUi`.
   *
   * See also: `https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl.html`
   */
  fun setupUI(builder: Panel) {}

  /**
   * Applies data from UI into project model or settings. It executes
   * for new project and new module.
   *
   * Wizard type (NPW or NMW) can be determined by [context] property
   * [WizardContext.isCreatingNewProject].
   */
  fun setupProject(project: Project) {}

  @ApiStatus.Internal
  fun createProjectConfigurator(): ProjectConfigurator? = null

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

    val MODIFIABLE_MODULE_MODEL_KEY: Key<ModifiableModuleModel> = Key.create("MODIFIABLE_MODULE_MODEL_KEY")

  }
}