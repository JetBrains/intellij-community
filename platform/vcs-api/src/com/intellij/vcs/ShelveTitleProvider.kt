// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.util.function.Consumer

interface ShelveTitleProvider {

  /**
   * Create a custom title for shelved changes
   */
  fun suggestTitle(project: Project, patch: ShelveTitlePatch, rename: Consumer<String>): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<ShelveTitleProvider> = ExtensionPointName<ShelveTitleProvider>("com.intellij.vcs.shelve.name")
  }
}

data class ShelveTitlePatch(val patchText: String, val fileNumber: Int)