// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.db

import com.intellij.ide.startup.importSettings.models.BundledEditorColorScheme
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager

object KnownColorSchemes {
  val Light: BundledEditorColorScheme? = findScheme("IntelliJ Light")
  val Darcula: BundledEditorColorScheme? = findScheme("Darcula")
  val HighContrast: BundledEditorColorScheme? = findScheme("High contrast")

  private fun findScheme(name: String): BundledEditorColorScheme? = BundledEditorColorScheme.fromManager(name) ?: run {
    logger.error(
      "Unable to find bundled color scheme \"$name\". " +
      "All available schemes: ${EditorColorsManager.getInstance().allSchemes.joinToString(", ", "\"", "\"") { it.name }}.")
    null
  }
}

private val logger = logger<KnownColorSchemes>()
