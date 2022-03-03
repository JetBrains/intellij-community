// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.util.UserDataHolderBase

class RootNewProjectWizardStep(override val context: WizardContext) : NewProjectWizardStep {

  override val data = UserDataHolderBase()

  override val propertyGraph = PropertyGraph("New project wizard")

  override var keywords = NewProjectWizardStep.Keywords()
}