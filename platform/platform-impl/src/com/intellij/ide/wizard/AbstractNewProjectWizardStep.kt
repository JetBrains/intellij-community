// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

abstract class AbstractNewProjectWizardStep(parentStep: NewProjectWizardStep) : NewProjectWizardStep {

  final override val context by parentStep::context

  final override val propertyGraph by parentStep::propertyGraph
}