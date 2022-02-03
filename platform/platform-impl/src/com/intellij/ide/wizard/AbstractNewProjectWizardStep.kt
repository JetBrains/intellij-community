// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

/**
 * Abstract implementation of child wizard step.
 * Needed to provide wizard data from parent into all descendant steps.
 * E.g. [context] and [propertyGraph] should be common for all steps from one wizard screen.
 *
 * @see chain
 * @see AbstractNewProjectWizardMultiStep
 * @see NewProjectWizardMultiStepFactory
 */
abstract class AbstractNewProjectWizardStep(parentStep: NewProjectWizardStep) : NewProjectWizardStep {

  final override val context by parentStep::context

  final override val propertyGraph by parentStep::propertyGraph

  final override val data by parentStep::data

  final override val keywords by parentStep::keywords
}