// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PlatformUtils
import com.jetbrains.builtInHelp.settings.SettingsPage
import org.jetbrains.builtInWebServer.BuiltInServerOptions
import java.awt.Desktop
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Created by Egor.Malyshev on 7/18/2017.
 */
@Suppress("unused")
class BuiltInHelpManager : HelpManager() {
  private val LOG = Logger.getInstance(javaClass)
  override fun invokeHelp(helpId: String?) {

    try {
      var url = "http://127.0.0.1:${BuiltInServerOptions.getInstance().effectiveBuiltInServerPort}/help/?${if (helpId != null) URLEncoder.encode(
        helpId, StandardCharsets.UTF_8)
      else "top"}"
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

        val info = ApplicationInfoEx.getInstanceEx()
        val productVersion = info.majorVersion + "." + info.minorVersion.substringBefore(".")

        var baseUrl = Utils.getStoredValue(SettingsPage.OPEN_HELP_BASE_URL,
                                           Utils.BASE_HELP_URL)

        if (!baseUrl.endsWith("/")) baseUrl += "/"

        url = "${baseUrl}help/$productWebPath/$productVersion/?$helpId"

        if (PlatformUtils.isJetBrainsProduct() && baseUrl == Utils.BASE_HELP_URL) {
          val productCode = info.build.productCode
          if (!StringUtil.isEmpty(productCode)) {
            url += "&utm_source=from_product&utm_medium=help_link&utm_campaign=$productCode&utm_content=$productVersion"
          }
        }
      }

      val browserName = java.lang.String.valueOf(
        Utils.getStoredValue(SettingsPage.USE_BROWSER, BuiltInHelpBundle.message("use.default.browser")))
      if (browserName == BuiltInHelpBundle.message("use.default.browser")) {
        if (Desktop.isDesktopSupported()) {
          Desktop.getDesktop().browse(URI(url))
        }
        else BrowserLauncher.instance.browse(url, WebBrowserManager.getInstance().firstActiveBrowser)

      }
      else BrowserLauncher.instance.browse(url, WebBrowserManager.getInstance().findBrowserById(browserName))

    }
    catch (e: URISyntaxException) {
      LOG.error("Help id '$helpId' produced an invalid URL.", e)
    }
    catch (e: IOException) {
      LOG.error("Cannot load help for '$helpId'.", e)
    }
  }
}