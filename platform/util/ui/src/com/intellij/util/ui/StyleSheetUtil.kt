// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.ui

import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.annotations.ApiStatus
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.swing.UIManager
import javax.swing.text.html.StyleSheet

object StyleSheetUtil {
  @get:ApiStatus.Internal
  val NO_GAPS_BETWEEN_PARAGRAPHS_STYLE by lazy {
    loadStyleSheet("p { margin-top: 0; }")
  }

  @JvmStatic
  fun getDefaultStyleSheet(): StyleSheet {
    val sheet = StyleSheet()
    val globalStyleSheet = UIManager.getDefaults().get("HTMLEditorKit.jbStyleSheet") as? StyleSheet
    if (globalStyleSheet == null) {
      if (!GraphicsEnvironment.isHeadless()) {
        thisLogger().warn("Missing global CSS sheet")
      }
      return sheet
    }

    // return a linked sheet to avoid mutation of a global variable
    sheet.addStyleSheet(globalStyleSheet)
    return sheet
  }

  @JvmStatic
  fun loadStyleSheet(input: String): StyleSheet {
    val styleSheet = StyleSheet()
    try {
      styleSheet.loadRules(StringReader(input), null)
    }
    catch (e: IOException) {
      throw RuntimeException(e) // shouldn't happen
    }
    return styleSheet
  }

  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class)
  fun loadStyleSheet(input: InputStream, ref: URL? = null): StyleSheet {
    val result = StyleSheet()
    result.loadRules(InputStreamReader(input, StandardCharsets.UTF_8), ref)
    return result
  }

  @Deprecated(message = "Use loadStyleSheet(InputStream)")
  fun loadStyleSheet(url: URL): StyleSheet? {
    try {
      val result = StyleSheet()
      result.loadRules(InputStreamReader(url.openStream(), StandardCharsets.UTF_8), url)
      return result
    }
    catch (e: IOException) {
      thisLogger().warn("$url loading failed", e)
      return null
    }
  }
}