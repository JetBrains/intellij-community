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

  private val anyUrlInvalidContributor = object : PluginRepositoryAuthProvider {
    override fun canHandle(domain: String): Boolean = true
    override fun getAuthHeaders(url: String?): Map<String, String> = headers
  }

  private inner class FakeUrlMatchingContributor(private val myDomain: String): PluginRepositoryAuthProvider {
    override fun canHandle(domain: String): Boolean = (domain == myDomain)
    override fun getAuthHeaders(url: String?): Map<String, String>  = headers
  }

  @Test
  fun basicTest() {
    setupContributors(FakeUrlMatchingContributor(fooDomain))
    assertEquals(PluginRepositoryAuthService().getAllCustomHeaders(fooUrl), headers)
  }

  @Test
  fun singleUrlHandling() {
    val authService = PluginRepositoryAuthService()
    setupContributors(anyUrlInvalidContributor)
    assertEquals(authService.getAllCustomHeaders(fooUrl), headers)
    assertEquals(authService.getAllCustomHeaders(barUrl), emptyMap())
  }

  @Test
  fun singleContributorPerUrl() {
    val authService = PluginRepositoryAuthService()
    setupContributors(FakeUrlMatchingContributor(fooDomain), FakeUrlMatchingContributor(fooDomain))
    assertEquals(authService.getAllCustomHeaders(fooUrl), emptyMap())
  }

  private fun setupContributors(vararg contributor: PluginRepositoryAuthProvider) {
    ExtensionTestUtil.maskExtensions(PluginRepositoryAuthProvider.EP_NAME, contributor.asList(), disposableRule.disposable)
  }
}