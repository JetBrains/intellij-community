// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@Suppress("DEPRECATION")
@JvmDefaultWithCompatibility
@Deprecated("Use LanguageGeneratorNewProjectWizard instead")
@ApiStatus.Internal
interface LanguageNewProjectWizard : NewProjectWizardMultiStepFactory<NewProjectWizardLanguageStep> {

  @ApiStatus.Internal
  companion object {

    @JvmField
    val EP_NAME: ExtensionPointName<LanguageNewProjectWizard> = ExtensionPointName("com.intellij.newProjectWizard.language")
  }
}
