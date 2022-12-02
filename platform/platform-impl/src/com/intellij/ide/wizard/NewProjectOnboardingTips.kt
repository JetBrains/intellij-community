// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.wizard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@JvmDefaultWithCompatibility
interface NewProjectOnboardingTips {
  @RequiresEdt
  fun installTips(project: Project, simpleSampleText: String)

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<NewProjectOnboardingTips>("com.intellij.newProject.onboarding.tips")
  }
}
