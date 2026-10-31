// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.help.HelpTarget
import com.intellij.openapi.help.HelpTargetProvider
import com.intellij.openapi.help.WebHelpProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

@TestApplication
class HelpTargetProviderTest {
  private val targets = ExtensionPointName.create<HelpTargetProvider>("com.intellij.helpTargetProvider")
  private val webHelp = ExtensionPointName.create<WebHelpProvider>("com.intellij.webHelpProvider")

  @Test
  fun `null topic reaches the provider as top`(@TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(webHelp, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(targets, listOf(provider()), disposable)
    assertEquals("http://127.0.0.1/help/?top", HelpManagerImpl.getHelpUrl(null))
  }

  @Test
  fun `direct URL callers use the provider`(@TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(webHelp, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(targets, listOf(provider()), disposable)
    assertEquals("http://127.0.0.1/help/?region-settings", HelpManagerImpl.getHelpUrl("region-settings"))
  }

  @Test
  fun `providers can decline a topic`(@TestDisposable disposable: Disposable) {
    ExtensionTestUtil.maskExtensions(webHelp, emptyList(), disposable)
    val declining = object : HelpTargetProvider {
      override fun getHelpTarget(helpId: String): HelpTarget? = null
    }
    ExtensionTestUtil.maskExtensions(targets, listOf(declining, provider()), disposable)
    assertEquals("http://127.0.0.1/help/?top", HelpManagerImpl.getHelpUrl("top"))
  }

  @Test
  fun `plugin web help takes precedence`(@TestDisposable disposable: Disposable) {
    val webProvider = object : WebHelpProvider() {
      override fun getHelpTopicPrefix(): String = "third.party."
      override fun getHelpPageUrl(helpTopicId: String): String = "https://example.com/plugin-help"
    }
    ExtensionTestUtil.maskExtensions(webHelp, listOf(webProvider), disposable)
    ExtensionTestUtil.maskExtensions(targets, listOf(provider()), disposable)
    assertEquals("https://example.com/plugin-help", HelpManagerImpl.getHelpUrl("third.party.topic"))
  }

  @Test
  fun `help invocation opens the provider target in its browser`(@TestDisposable disposable: Disposable) {
    val browser = WebBrowserManager.getInstance().browsers.first()
    val targetUrl = "http://127.0.0.1/help/?top"
    var opened: Pair<String, WebBrowser?>? = null
    val launcher = object : BrowserLauncher() {
      override fun open(url: String) { error("Unexpected open") }
      override fun browse(file: Path) { error("Unexpected file") }
      override fun browse(url: String, browser: WebBrowser?, project: Project?) { opened = url to browser }
    }
    ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, launcher, disposable)
    ExtensionTestUtil.maskExtensions(webHelp, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(targets, listOf(object : HelpTargetProvider {
      override fun getHelpTarget(helpId: String): HelpTarget = HelpTarget(targetUrl, browser.id.toString())
    }), disposable)
    HelpManagerImpl().invokeHelp(null)
    assertEquals(targetUrl to browser, opened)
  }

  private fun provider(): HelpTargetProvider = object : HelpTargetProvider {
    override fun getHelpTarget(helpId: String): HelpTarget = HelpTarget("http://127.0.0.1/help/?$helpId")
  }
}
