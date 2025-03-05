// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.ProjectConfigurator
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.annotations.ApiStatus


/**
 * Creates step which delegates all calls to [steps] in order from first to last.
 *
 * Needed to build one horizontal step from several dependent vertical steps.
 * For example, you can merge base, comment, main and assets steps into one step,
 * which will be shown on one wizard page.
 *
 * @see NewProjectWizardStep
 */
class NewProjectWizardChainStep<S : NewProjectWizardStep> : AbstractNewProjectWizardStep {

  private val step: S
  private val steps: List<NewProjectWizardStep> // including this.step

  constructor(step: S) : this(step, emptyList())

  private constructor(step: S, descendantSteps: List<NewProjectWizardStep>) : super(step) {
    this.step = step
    this.steps = descendantSteps + step
  }

  /**
   * Appends new child step into steps chain.
   */
  fun <NS : NewProjectWizardStep> nextStep(create: (S) -> NS): NewProjectWizardChainStep<NS> {
    return NewProjectWizardChainStep(create(step), steps)
  }

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

  @ApiStatus.Internal
  override fun createProjectConfigurator(): ProjectConfigurator? {
    return steps.firstNotNullOfOrNull { it.createProjectConfigurator() }
  }

  companion object {

    fun <S : NewProjectWizardStep, NS : NewProjectWizardStep> S.nextStep(create: (S) -> NS): NewProjectWizardChainStep<NS> {
      return NewProjectWizardChainStep(this).nextStep(create)
    }
  }
}