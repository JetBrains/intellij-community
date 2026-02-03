// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.wizard

import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.Panel

/**
 * Creates a step which delegates all calls to [steps] in order from first to last.
 *
 * Needed to build one horizontal step from several dependent vertical steps.
 * For example, you can merge base, comment, main and assets steps into one step,
 * which will be shown on one wizard page.
 *
 * @see NewProjectWizardStep
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/new-project-wizard.html#chaining-steps">
 *   New Project Wizard API: Chaining Steps (IntelliJ Platform Docs)</a>
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
   * Appends a new child step into the step chain.
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

  companion object {

    fun <S : NewProjectWizardStep, NS : NewProjectWizardStep> S.nextStep(create: (S) -> NS): NewProjectWizardChainStep<NS> {
      return NewProjectWizardChainStep(this).nextStep(create)
    }
  }
}