// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.l10n.LocalizationStateService
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class HtmlVisionHelper {
  companion object {
    private const val THEME_KEY = "\$__VISION_PAGE_SETTINGS_THEME__$"
    private const val DARK_THEME = "dark"
    private const val LIGHT_THEME = "light"

    private const val LANG_KEY = "\$__VISION_PAGE_SETTINGS_LANGUAGE_CODE__$"
    private const val DEFAULT_LANG = "en-us"

    /** Marker that REGION of IDE is China (not the language of IDE or page) */
    private const val IS_CHINESE_KEY = "\$__VISION_PAGE_SETTINGS_IS_CHINESE__$"

    @ApiStatus.Internal
    fun getMIMEType(extension: String): String = when (extension) {
      "gif" -> "image/gif"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "svg" -> "image/svg+xml"
      "mp4" -> "video/mp4"
      "webm" -> "video/webm"
      "webp" -> "image/webp"
      else -> "".also { LOG.warn("Unknown file extension: '$extension' for MIME type. Falling back to '$it'.") }
    }

    @ApiStatus.Internal
    fun processContent(content: String, publicVarsPattern: Regex): String =
      content.replace(publicVarsPattern) {
        when (it.value) {
          THEME_KEY -> if (StartupUiUtil.isDarkTheme) DARK_THEME else LIGHT_THEME
          IS_CHINESE_KEY -> (RegionSettings.getRegion() == Region.CHINA).toString()
          LANG_KEY -> LocalizationStateService.getInstance()?.selectedLocale?.lowercase() ?: run {
            LOG.error("Cannot get a LocalizationStateService instance. Default to $DEFAULT_LANG locale.")
            DEFAULT_LANG
          }
          else -> it.value
        }
      }
  }
}

private val LOG = logger<HtmlVisionHelper>()