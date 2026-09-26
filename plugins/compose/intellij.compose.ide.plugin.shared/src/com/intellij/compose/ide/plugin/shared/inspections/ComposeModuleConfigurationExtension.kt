// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point that abstracts Compose configuration checks and quick-fix creation
 * for a concrete module/build-system type.
 */
@ApiStatus.Internal
interface ComposeModuleConfigurationExtension {

  /**
   * Returns `true` when this implementation can handle Compose configuration for [module].
   */
  fun isApplicable(module: Module): Boolean

  /**
   * Returns `true` when Compose support is already enabled in [module].
   */
  fun hasComposeEnabled(module: Module): Boolean

  /**
   * Creates a quick fix that enables Compose support in [module].
   */
  fun createEnableComposeQuickFix(module: Module): LocalQuickFix

  companion object {
    val EP_NAME: ExtensionPointName<ComposeModuleConfigurationExtension> =
      ExtensionPointName.create("com.intellij.compose.ide.plugin.shared.composeModuleConfiguration")

    fun findFor(module: Module): ComposeModuleConfigurationExtension? =
      EP_NAME.extensionList.firstOrNull { extension -> extension.isApplicable(module) }
  }
}
