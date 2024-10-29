// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.intellij.ide.BrowserUtil
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import com.jetbrains.builtInHelp.settings.SettingsPage
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import java.io.IOException
import java.lang.String.*
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val LOG = Logger.getInstance(BuiltInHelpManager::class.java)

class BuiltInHelpManager : HelpManager() {

  override fun invokeHelp(helpId: String?) {

    val helpIdToUse = URLEncoder.encode(helpId, StandardCharsets.UTF_8) ?: "top"
    logWillOpenHelpId(helpIdToUse)

    try {
      var activeKeymap: Keymap? = KeymapManagerEx.getInstanceEx().getActiveKeymap()
      if (true == activeKeymap?.canModify())
        activeKeymap = activeKeymap.parent

      val activeKeymapParam = if (activeKeymap == null) "" else "&keymap=${URLEncoder.encode(activeKeymap.presentableName, StandardCharsets.UTF_8)}"

      var url = "http://127.0.0.1:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}/help/?${
        helpIdToUse
      }$activeKeymapParam"

      val tryOpenWebSite = java.lang.Boolean.valueOf(Utils.getStoredValue(
        SettingsPage.OPEN_HELP_FROM_WEB, "true"))

      var online = false
      if (tryOpenWebSite) {
        online = try {
          InetAddress.getByName("www.jetbrains.com").isReachable(100)
        }
        catch (e: Exception) {
          false
        }
      }

      if (online) {

        val nameInfo = ApplicationNamesInfo.getInstance()
        val editionName = nameInfo.editionName

        val productWebPath = when (val productName = StringUtil.toLowerCase(nameInfo.productName)) {
          "rubymine", "ruby" -> "ruby"
          "intellij idea", "idea" -> "idea"
          "goland" -> "go"
          "appcode" -> "objc"
          "pycharm" -> if (editionName != null && StringUtil.toLowerCase(editionName) == "edu") "pycharm-edu" else "pycharm"
          else -> productName
        }

        val info = ApplicationInfo.getInstance()
        val productVersion = info.shortVersion

        var baseUrl = Utils.getStoredValue(SettingsPage.OPEN_HELP_BASE_URL,
                                           Utils.BASE_HELP_URL)

        if (!baseUrl.endsWith("/")) baseUrl += "/"

        url = "${baseUrl}help/$productWebPath/$productVersion/?${helpIdToUse}"

        if (PlatformUtils.isJetBrainsProduct() && baseUrl == Utils.BASE_HELP_URL) {
          val productCode = info.build.productCode
          if (!StringUtil.isEmpty(productCode)) {
            url += "&utm_source=from_product&utm_medium=help_link&utm_campaign=$productCode&utm_content=$productVersion$activeKeymapParam"
          }
        }
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