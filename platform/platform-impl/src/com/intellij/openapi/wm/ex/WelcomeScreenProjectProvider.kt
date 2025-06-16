// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows identifying projects that act as a welcome screen tab.
 * This is needed for customizing actions context.
 *
 * E.g., if a project is created/opened/cloned from a welcome screen project,
 * we should close the welcome screen project to preserve the welcome screen experience.
 */
@ApiStatus.Internal
interface WelcomeScreenProjectProvider {
  companion object {
    val EP_NAME: ExtensionPointName<WelcomeScreenProjectProvider> = ExtensionPointName("com.intellij.welcomeScreenProjectProvider")
  }

  fun isWelcomeScreenProject(project: Project): Boolean
}
