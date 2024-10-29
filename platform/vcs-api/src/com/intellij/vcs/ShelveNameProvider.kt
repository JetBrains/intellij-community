// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.LocalChangeList.getAllDefaultNames
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

interface ShelveNameProvider {

  /**
   * Create a custom name for shelved changes
   */
  suspend fun generateName(project: Project, patch: ShelveNamePatch): String?

  /**
   * Show got it tooltip popup if applicable
   */
  @ApiStatus.Internal
  fun showGotItPopup(project: Project, component: Component): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<ShelveNameProvider> = ExtensionPointName<ShelveNameProvider>("com.intellij.vcs.shelve.name")

    @JvmStatic
    fun showGotItTooltip(project: Project, component: Component) {
      EP_NAME.extensionList.any { it.showGotItPopup(project, component) }
    }

    @JvmStatic
    suspend fun generateShelveName(project: Project, patch: ShelveNamePatch): String? {
      return EP_NAME.getIterable().firstNotNullOfOrNull { it?.generateName(project, patch) }
    }

    @JvmStatic
    fun hasDefaultName(name: String): Boolean = getAllDefaultNames().contains(name)
  }
}

data class ShelveNamePatch(val patchText: String, val fileNumber: Int)