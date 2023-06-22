// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.UserDataHolder

/**
 * Abstract implementation of child wizard step.
 * Needed to provide wizard data from parent into all descendant steps.
 * E.g. [context] and [propertyGraph] should be common for all steps from one wizard screen.
 *
 * @see com.intellij.ide.wizard.NewProjectWizardChainStep
 * @see AbstractNewProjectWizardMultiStep
 * @see NewProjectWizardMultiStepFactory
 */
abstract class AbstractNewProjectWizardStep(parentStep: NewProjectWizardStep) : NewProjectWizardStep {

  final override val context: WizardContext by parentStep::context

  final override val propertyGraph: PropertyGraph by parentStep::propertyGraph

  final override val data: UserDataHolder by parentStep::data

  final override val keywords: NewProjectWizardStep.Keywords by parentStep::keywords
}