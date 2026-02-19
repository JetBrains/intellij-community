// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.auth.PluginRepositoryAuthProvider
import com.intellij.ide.plugins.auth.PluginRepositoryAuthService
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class PluginRepositoryAuthServiceTest {

  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  private val headers = mapOf("Header" to "DATA")
  private val fooDomain = "foo.bar"
  private val fooUrl = "https://foo.bar/foo?a=b&c=d"
  private val barUrl = "https://bar.baz/baz/zaz"

  private val unescapedUrl = "https://buildserver.labs.intellij.net/guestAuth/repository/download/Documentation_Stardust/lastSuccessful/updatePlugins.xml?branch=master-internal &build=IU-221.3427.103"

  private inner class FakeUrlMatchingContributor(private val myDomain: String,
                                                 private val contributorHeaders: Map<String, String> = headers): PluginRepositoryAuthProvider {
    override fun canHandle(url: String): Boolean = (url.contains(myDomain))
    override fun getAuthHeaders(url: String): Map<String, String>  = contributorHeaders
  }

  @Test
  fun basicTest() {
    setupContributors(FakeUrlMatchingContributor(fooDomain))
    assertEquals(headers, PluginRepositoryAuthService().getAllCustomHeaders(fooUrl))
  }

  @Test
  fun `no headers are returned for an unmatched URL`() {
    val authService = PluginRepositoryAuthService()
    setupContributors(FakeUrlMatchingContributor(fooDomain))
    assertEquals(headers, authService.getAllCustomHeaders(fooUrl))
    assertEquals(emptyMap(), authService.getAllCustomHeaders(barUrl))
  }

  @Test
  fun `only the first contributor is used in case of multiple matching`() {
    val authService = PluginRepositoryAuthService()
    setupContributors(FakeUrlMatchingContributor(fooDomain), FakeUrlMatchingContributor(fooDomain, mapOf("foo" to "bar")))
    assertEquals(headers, authService.getAllCustomHeaders(fooUrl))
  }

  @Test
  fun `malformed URL doesn't crash getAllCustomHeaders`() {
    val authService = PluginRepositoryAuthService()
    authService.getAllCustomHeaders(unescapedUrl)
  }

  @Test
  fun `getAllCustomHeaders doesn't crash when no contributors match`() {
    val authService = PluginRepositoryAuthService()
    setupContributors(FakeUrlMatchingContributor(fooDomain))
    authService.getAllCustomHeaders(barUrl)
    authService.getAllCustomHeaders(fooUrl)
    authService.getAllCustomHeaders(barUrl)
    assertEquals(headers, authService.getAllCustomHeaders(fooUrl))
  }

  private fun setupContributors(vararg contributor: PluginRepositoryAuthProvider) {
    ExtensionTestUtil.maskExtensions(PluginRepositoryAuthProvider.EP_NAME, contributor.asList(), disposableRule.disposable)
  }
}