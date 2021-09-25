// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.auth.PluginHostAuthContributor
import com.intellij.ide.plugins.auth.PluginHostAuthService
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PluginHostAuthServiceTest {

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  private val headers = mapOf("Header" to "DATA")
  private val fooUrl = "https://foo.bar"
  private val barUrl = "https://bar.baz"

  private val anyUrlInvalidContributor = object : PluginHostAuthContributor {
    override fun canHandle(url: String): Boolean = true
    override fun getCustomHeaders(): Map<String, String> = headers
  }

  private inner class FakeUrlMatchingContributor(private val myUrl: String): PluginHostAuthContributor {
    override fun canHandle(url: String): Boolean = (url == myUrl)
    override fun getCustomHeaders(): Map<String, String> = headers
  }

  @Test
  fun basicTest() {
    setupContributors(FakeUrlMatchingContributor(fooUrl))
    assertEquals(PluginHostAuthService().getAllCustomHeaders(fooUrl), headers)
  }

  @Test
  fun singleUrlHandling() {
    val authService = PluginHostAuthService()
    setupContributors(anyUrlInvalidContributor)
    assertEquals(authService.getAllCustomHeaders(fooUrl), headers)
    assertEquals(authService.getAllCustomHeaders(barUrl), emptyMap())
  }

  @Test
  fun singleContributorPerUrl() {
    val authService = PluginHostAuthService()
    setupContributors(FakeUrlMatchingContributor(fooUrl))
    assertEquals(authService.getAllCustomHeaders(fooUrl), headers)
    setupContributors(FakeUrlMatchingContributor(fooUrl))
    assertEquals(authService.getAllCustomHeaders(fooUrl), emptyMap())
  }

  private fun setupContributors(vararg contributor: PluginHostAuthContributor) {
    val extensionPoint = PluginHostAuthContributor.EP_NAME.point
    contributor.forEach {
      extensionPoint.registerExtension(it, disposableRule.disposable)
    }
  }
}