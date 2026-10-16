// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.osc8

import com.intellij.diagnostic.PluginException
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.UrlFilter
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CoroutineScope
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class Osc8UriResolverTest {
  private val first = OpenUrlHyperlinkInfo("https://first.example")
  private val second = OpenUrlHyperlinkInfo("https://second.example")

  @Test
  fun `the first recognizing resolver wins`() {
    val composite = CompositeOsc8UriResolver(listOf(fixedResolver(null), fixedResolver(first), fixedResolver(second)))
    assertThat(composite.resolve("x")).isSameAs(first)
  }

  @Test
  fun `no recognizing resolver gives null`() {
    val composite = CompositeOsc8UriResolver(listOf(fixedResolver(null), fixedResolver(null)))
    assertThat(composite.resolve("x")).isNull()
  }

  @Test
  fun `a failing resolver is reported and skipped`() {
    val failing = object : Osc8UriResolver {
      override fun resolve(uri: String): HyperlinkInfo = throw IllegalStateException("broken")
    }
    val composite = CompositeOsc8UriResolver(listOf(failing, fixedResolver(first)))
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      assertThat(composite.resolve("x")).isSameAs(first)
    }
    assertThat(error).isInstanceOf(PluginException::class.java).hasCauseInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun `a failing provider does not hide the others`(@TestDisposable testDisposable: Disposable): Unit = timeoutRunBlocking {
    val failing = object : Osc8UriResolverProvider {
      override fun getResolvers(project: Project): List<Osc8UriResolver> = throw IllegalStateException("broken")
    }
    ExtensionTestUtil.maskExtensions(Osc8UriResolverProvider.EP_NAME, listOf(failing, fixedResolverProvider(first)), testDisposable)
    val wrapper = createWrapper()
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      assertThat(wrapper.getCompositeResolver().resolve("x")).isSameAs(first)
    }
    assertThat(error).isInstanceOf(PluginException::class.java).hasCauseInstanceOf(IllegalStateException::class.java)
  }

  @Test
  fun `the wrapper follows the extensions`(@TestDisposable testDisposable: Disposable): Unit = timeoutRunBlocking {
    val wrapper = createWrapper()
    var updates = 0
    wrapper.addUpdateListener { updates++ }
    val maskDisposable = Disposer.newDisposable(testDisposable, "masked URI resolver providers")
    // Registering the provider fires the change event.
    ExtensionTestUtil.maskExtensions(Osc8UriResolverProvider.EP_NAME, listOf(fixedResolverProvider(first)), maskDisposable)
    val updatesAfterMask = updates
    assertThat(updatesAfterMask).isGreaterThan(0)
    assertThat(wrapper.getCompositeResolver().resolve("x")).isSameAs(first)

    // Deregistering the provider fires the change event.
    Disposer.dispose(maskDisposable)
    assertThat(updates).isGreaterThan(updatesAfterMask)
    assertThat(wrapper.getCompositeResolver().resolve("x")).isNull()
    assertThat(wrapper.getCompositeResolver().resolve("https://example.com")).isInstanceOf(OpenUrlHyperlinkInfo::class.java)
  }

  @Test
  fun `web url is a link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("https://example.com/path")).isInstanceOf(OpenUrlHyperlinkInfo::class.java)
  }

  @Test
  fun `file url is a file link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("file:///tmp/notes.txt")).isInstanceOf(UrlFilter.FileUrlHyperlinkInfo::class.java)
  }

  @Test
  fun `url inside other text is not a link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("see https://example.com for details")).isNull()
  }

  @Test
  fun `plain text is not a link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("not a url")).isNull()
  }

  @Test
  fun `jetbrains protocol uri is a link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("jetbrains://idea/navigate/reference?project=p&path=a.txt")).isNotNull()
  }

  @Test
  fun `jetbrains protocol uri without a command is not a link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("jetbrains://")).isNull()
  }

  @Test
  fun `jetbrains protocol uri inside other text is not a link`(): Unit = timeoutRunBlocking {
    assertThat(resolve("see jetbrains://idea/navigate/reference")).isNull()
  }

  private fun CoroutineScope.createWrapper(): CompositeOsc8UriResolverWrapper {
    return CompositeOsc8UriResolverWrapper(ProjectManager.getInstance().defaultProject, this)
  }

  private fun CoroutineScope.resolve(uri: String): HyperlinkInfo? {
    return createWrapper().getCompositeResolver().resolve(uri)
  }

  private fun fixedResolver(fixedResult: HyperlinkInfo?): Osc8UriResolver = object : Osc8UriResolver {
    override fun resolve(uri: String): HyperlinkInfo? = fixedResult
  }

  private fun fixedResolverProvider(result: HyperlinkInfo?): Osc8UriResolverProvider = object : Osc8UriResolverProvider {
    override fun getResolvers(project: Project): List<Osc8UriResolver> = listOf(fixedResolver(result))
  }
}
