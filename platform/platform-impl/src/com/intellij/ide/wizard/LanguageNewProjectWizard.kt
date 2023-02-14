// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName

@JvmDefaultWithCompatibility
interface LanguageNewProjectWizard : NewProjectWizardMultiStepFactory<NewProjectWizardLanguageStep> {
  companion object {
    val EP_NAME = ExtensionPointName<LanguageNewProjectWizard>("com.intellij.newProjectWizard.language")
  }
}
