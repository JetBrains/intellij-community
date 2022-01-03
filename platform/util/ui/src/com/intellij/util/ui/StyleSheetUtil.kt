// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.ui

import com.intellij.openapi.diagnostic.thisLogger
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.swing.UIManager
import javax.swing.text.html.StyleSheet

object StyleSheetUtil {
  @JvmStatic
  fun getDefaultStyleSheet(): StyleSheet {
    val sheet = StyleSheet()
    val globalStyleSheet = UIManager.getDefaults().get("HTMLEditorKit.jbStyleSheet") as? StyleSheet
    if (globalStyleSheet == null) {
      thisLogger().warn("Missing global CSS sheet")
      return sheet
    }
    // return a linked sheet to avoid mutation of a global variable
    sheet.addStyleSheet(globalStyleSheet)
    return sheet
  }

  @JvmStatic
  fun createStyleSheet(css: String): StyleSheet {
    val styleSheet = StyleSheet()
    try {
      styleSheet.loadRules(StringReader(css), null)
    }
    catch (e: IOException) {
      throw RuntimeException(e) // shouldn't happen
    }
    return styleSheet
  }

  @JvmStatic
  fun loadStyleSheet(url: URL?): StyleSheet? {
    if (url == null) {
      return null
    }

    return try {
      StyleSheet().apply {
        loadRules(InputStreamReader(url.openStream(), StandardCharsets.UTF_8), url)
      }
    }
    catch (e: IOException) {
      thisLogger().warn("$url loading failed", e)
      null
    }
  }
}