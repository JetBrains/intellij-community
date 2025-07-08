// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows identifying projects that act as a welcome screen tab.
 * This is needed for customizing actions context.
 *
 * E.g., if a project is created/opened/cloned from a welcome screen project,
 * we should close the welcome screen project to preserve the welcome screen experience.
 *
 * This customization is intended to be used per-IDE, not per language.
 */
@ApiStatus.Internal
abstract class WelcomeScreenProjectProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<WelcomeScreenProjectProvider> = ExtensionPointName("com.intellij.welcomeScreenProjectProvider")

    private fun getSingleExtension(): WelcomeScreenProjectProvider? {
      val providers = EP_NAME.extensionList
      if (providers.isEmpty()) return null
      if (providers.size > 1) {
        thisLogger().warn("Multiple WelcomeScreenProjectProvider extensions")
        return null
      }
      return providers.first()
    }

    @JvmStatic
    fun isWelcomeScreenProject(project: Project): Boolean {
      val extension = getSingleExtension() ?: return false
      return extension.doIsWelcomeScreenProject(project)
    }

    @JvmStatic
    fun isForceDisabledFileColors(): Boolean {
      val extension = getSingleExtension() ?: return false
      return extension.doIsForceDisabledFileColors()
    }
  }

  protected abstract fun doIsWelcomeScreenProject(project: Project): Boolean

  protected abstract fun doIsForceDisabledFileColors(): Boolean
}
