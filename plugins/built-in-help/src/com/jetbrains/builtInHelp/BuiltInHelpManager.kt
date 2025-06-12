// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.jetbrains.builtInHelp.settings.SettingsPage
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import java.io.IOException
import java.lang.String.valueOf
import java.net.*
import java.nio.charset.StandardCharsets

private val LOG = Logger.getInstance(BuiltInHelpManager::class.java)

class BuiltInHelpManager : HelpManager() {

  private fun isOnline(): Boolean {
    return try {
      val connection = URL("https://www.jetbrains.com/")
        .openConnection() as HttpURLConnection
      connection.connectTimeout = 1500
      connection.readTimeout = 1500
      connection.requestMethod = "GET"
      connection.connect()
      println("Response code: ${connection.responseCode}")
      connection.responseCode < 400
    }
    catch (e: IOException) {
      e.printStackTrace()
      false
    }
  }

  override fun invokeHelp(helpId: String?) {

    val helpIdToUse = URLEncoder.encode(helpId, StandardCharsets.UTF_8) ?: "top"
    logWillOpenHelpId(helpIdToUse)

    var activeKeymap: Keymap? = KeymapManagerEx.getInstanceEx().getActiveKeymap()
    if (true == activeKeymap?.canModify())
      activeKeymap = activeKeymap.parent

    try {
      val tryOpenWebSite = java.lang.Boolean.valueOf(Utils.getStoredValue(
        SettingsPage.OPEN_HELP_FROM_WEB, "true"))

      val url = if (tryOpenWebSite && isOnline()) {
        val helpUrl =
          ExternalProductResourceUrls.getInstance().helpPageUrl?.let { it(helpIdToUse) }

        if (activeKeymap != null)
          helpUrl?.addParameters(
            mapOf(Pair("keymap", activeKeymap.presentableName))
          )
        IdeUrlTrackingParametersProvider.getInstance().augmentUrl(helpUrl.toString())

      }
      else {

        val activeKeymapParam = if (activeKeymap == null) "" else "&keymap=${URLEncoder.encode(activeKeymap.presentableName, StandardCharsets.UTF_8)}"

        "http://127.0.0.1:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}/help/?${
          helpIdToUse
        }$activeKeymapParam"
      }

      val browserName = valueOf(
        Utils.getStoredValue(SettingsPage.USE_BROWSER, BuiltInHelpBundle.message("use.default.browser")))

      val browser = WebBrowserManager.getInstance().findBrowserById(browserName)

      if (browser == null || browserName == BuiltInHelpBundle.message("use.default.browser")) {
        BrowserUtil.browse(URI(url))
      }
      else {
        BrowserLauncher.instance.browse(url, browser)
      }
    }
    catch (e: URISyntaxException) {
      LOG.error("Help id '$helpIdToUse' produced an invalid URL.", e)
    }
    catch (e: IOException) {
      LOG.error("Cannot load help for '$helpIdToUse'.", e)
    }
  }
}