// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.thisLogger
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.swing.UIManager
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

object StyleSheetUtil {

  private val swingDefaultHtmlKitStyle by lazy<StyleSheet> {
    // get the default JRE CSS and ..
    val kit = HTMLEditorKit()
    val defaultSheet = kit.styleSheet
    // .. erase global ref to this CSS so no one can alter it
    kit.styleSheet = null
    defaultSheet
  }

  //language=css
  private val commonStyle by lazy {
    createStyleSheet(
      "code { font-size: 100%; }" +  // small by Swing's default
      "small { font-size: small; }" +  // x-small by Swing's default
      "a { text-decoration: none;}" +
      // override too large default margin "ul {margin-left-ltr: 50; margin-right-rtl: 50}" from javax/swing/text/html/default.css
      "ul { margin-left-ltr: 12; margin-right-rtl: 12; }" +
      // override too large default margin "ol {margin-left-ltr: 50; margin-right-rtl: 50}" from javax/swing/text/html/default.css
      // Select ol margin to have the same indentation as "ul li" and "ol li" elements (seems value 22 suites well)
      "ol { margin-left-ltr: 22; margin-right-rtl: 22; }"
    )
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
    if (url == null) return null

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

  /**
   * Applied to all JLabel instances, including subclasses. Supported in JBR only.
   * Generally should be called only once during startup
   */
  @JvmStatic
  fun configureCustomLabelStyle() {
    val activity = StartUpMeasurer.startActivity("html kit configuration")
    UIManager.getDefaults()["javax.swing.JLabel.userStyleSheet"] = createJBDefaultStyleSheet()
    activity.end()
  }

  @JvmStatic
  fun createJBDefaultStyleSheet(): StyleSheet {
    val style = StyleSheet()
    val defaultStyleSheet = UIManager.getDefaults()["StyledEditorKit.JBDefaultStyle"] as? StyleSheet ?: swingDefaultHtmlKitStyle
    style.addStyleSheet(defaultStyleSheet)
    style.addStyleSheet(commonStyle)
    return style
  }
}