@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.diagnostic.logger
import sun.awt.FontConfiguration
import sun.font.CompositeFontDescriptor
import sun.font.SunFontManager
import java.io.File
import java.nio.charset.Charset
import java.util.*

class IdeFontManager: SunFontManager() {
  companion object {
    internal fun getDefaultIdeFont(): Array<String>? {
      val interFontPath = System.getProperty("java.home") + File.separator + "lib" + File.separator +
                          "fonts" + File.separator + "Inter-Regular.otf"
      if (File(interFontPath).exists()) {
        return arrayOf("Inter", interFontPath)
      }

      return null
    }
  }

  override fun getFontPath(noType1Fonts: Boolean): String {
    return ""
  }

  override fun getDefaultPlatformFont(): Array<String> {
    return getDefaultIdeFont()
           ?: run {
             logger<IdeFontManager>().error("Default font is not available")
             return arrayOf("Dialog", "")
           }
  }

  override fun getInstalledFontFamilyNames(requestedLocale: Locale): Array<String> {
    return allInstalledFonts.mapTo(mutableSetOf()) { it.getFamily(requestedLocale) }.toTypedArray()
  }

  override fun createFontConfiguration(): FontConfiguration {
    return IdeFontConfiguration(this) // do not init - we do not have a config to read
  }

  override fun createFontConfiguration(preferLocaleFonts: Boolean, preferPropFonts: Boolean): FontConfiguration {
    return IdeFontConfiguration(this, preferLocaleFonts, preferPropFonts)
  }
}


private class IdeFontConfiguration : FontConfiguration {
  constructor(fm: SunFontManager)
    : super(fm)

  constructor(fm: SunFontManager, preferLocaleFonts: Boolean, preferPropFonts: Boolean) :
    super(fm, preferLocaleFonts, preferPropFonts)

  override fun getDefaultFontCharset(fontName: String): Charset {
    return Charset.forName("UTF-8")
  }

  override fun getEncoding(awtFontName: String, characterSubsetname: String): String {
    return "default"
  }

  override fun getFaceNameFromComponentFontName(arg0: String): String? {
    return null
  }

  override fun getFallbackFamilyName(fontName: String, defaultFallback: String): String {
    return defaultFallback
  }

  override fun getFileNameFromComponentFontName(componentFontName: String): String? {
    return getFileNameFromPlatformName(componentFontName)
  }

  override fun initReorderMap() {
    reorderMap = HashMap<String, Any>()
  }

  override fun getExtraFontPath(): String? {
    return null
  }

  override fun get2DCompositeFontInfo(): Array<CompositeFontDescriptor> {
    val fontNames = mutableListOf<String>()
    val fontPaths = mutableListOf<String>()

    val ideFont = IdeFontManager.getDefaultIdeFont()
    if (ideFont != null) {
      fontNames += ideFont[0]
      fontPaths += ideFont[1]
    }

    if (fontNames.isEmpty()) {
      return emptyArray()
    }

    val result = mutableListOf<CompositeFontDescriptor>()
    for (fontId in 0 until NUM_FONTS) {
      for (styleId in 0 until NUM_STYLES) {
        val faceName = publicFontNames[fontId] + "." + styleNames[styleId]
        result += CompositeFontDescriptor(faceName, 1, fontNames.toTypedArray(), fontPaths.toTypedArray(), null, null)
      }
    }
    return result.toTypedArray()
  }
}