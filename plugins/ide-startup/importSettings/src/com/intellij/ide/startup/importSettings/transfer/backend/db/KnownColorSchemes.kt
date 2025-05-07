// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.db

import com.intellij.ide.startup.importSettings.models.BundledEditorColorScheme
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.colors.EditorColorsManager

object KnownColorSchemes {
  val Light: BundledEditorColorScheme? = findScheme("Light")
  val Darcula: BundledEditorColorScheme? = findScheme("Dark")
  val HighContrast: BundledEditorColorScheme? = findScheme("High contrast")

  private fun findScheme(name: String): BundledEditorColorScheme? = BundledEditorColorScheme.fromManager(name) ?: run {
    val names = logger.runAndLogException { EditorColorsManager.getInstance().allSchemes.joinToString(", ", "\"", "\"") { it.name } }
                ?: "[cannot compute]"
    logger.error("Unable to find bundled color scheme \"$name\". All available schemes: ${names}.")
    null
  }
}

private val logger = logger<KnownColorSchemes>()
