// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.whatsNew

import com.intellij.ide.Region
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.function.Consumer

@TestApplication
class JcefWhatsNewPageProviderTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @ParameterizedTest
  @EnumSource(Region::class)
  fun `the page waits for its region before it opens and excludes China from shows`(region: Region): Unit = timeoutRunBlocking {
    val regionRequested = CompletableDeferred<Unit>()
    val resolvedRegion = CompletableDeferred<Region>()
    val opened = CompletableDeferred<Pair<Boolean, Boolean>>()
    openAfterRegionCheck(projectFixture.get(), true, Consumer { showAirPromo ->
      opened.complete(showAirPromo to ApplicationManager.getApplication().isDispatchThread)
    }) {
      regionRequested.complete(Unit)
      resolvedRegion.await()
    }

    regionRequested.await()
    assertFalse(opened.isCompleted)
    resolvedRegion.complete(region)
    assertEquals((region != Region.CHINA) to true, opened.await())
  }

  @Test
  fun `a page without code opens without a region check or a block show`(): Unit = timeoutRunBlocking {
    val opened = CompletableDeferred<Boolean>()
    openAfterRegionCheck(projectFixture.get(), false, Consumer { opened.complete(it) }) {
      error("A page without the promo needs no region check")
    }
    assertTrue(opened.isCompleted)
    assertFalse(opened.await())
  }

  @Test
  fun `the page trusts the origin of its URL and the origin of the web site that a short link redirects to`() {
    assertEquals(listOf("https://jb.gg", "https://www.jetbrains.com"),
                 trustedOrigins("https://jb.gg/whats-new?var=embed", "https://www.jetbrains.com/zh-cn"))
    assertEquals(listOf("https://www.jetbrains.com"), trustedOrigins("https://WWW.jetbrains.com/idea/whatsnew/", "https://www.jetbrains.com"))
    assertEquals(listOf("https://www.jetbrains.com.cn:8443"), trustedOrigins("https://www.jetbrains.com.cn:8443/idea/whatsnew/", null))
    assertEquals(emptyList<String>(), trustedOrigins("not a url", null))
  }

  @Test
  fun `the page gets the query handler only with a trusted origin`() {
    val handler = object : HTMLEditorProvider.JsQueryHandler {
      override suspend fun query(id: Long, request: String): String = ""
    }
    val withoutOrigins = HTMLEditorProvider.Request.url("https://example.com/whatsnew")
    setQueryHandler(withoutOrigins, handler, emptyList())
    assertNull(withoutOrigins.queryHandler)

    val withOrigins = HTMLEditorProvider.Request.url("https://example.com/whatsnew")
    setQueryHandler(withOrigins, handler, listOf("https://example.com"))
    assertSame(handler, withOrigins.queryHandler)
    assertEquals(listOf("https://example.com"), withOrigins.trustedOrigins)
  }
}

private fun openAfterRegionCheck(project: Project, hasAirPromo: Boolean, openPage: Consumer<Boolean>, currentRegion: suspend () -> Region) {
  val openingClass = Class.forName("com.intellij.ide.whatsNew.JcefWhatsNewPageOpening")
  val method = openingClass.getDeclaredMethod("openAfterRegionCheck", Boolean::class.javaPrimitiveType,
                                              Consumer::class.java, Function1::class.java)
  method.invoke(project.getService(openingClass), hasAirPromo, openPage, currentRegion)
}

private val providerClass: Class<*>
  get() = Class.forName("com.intellij.ide.whatsNew.JcefWhatsNewPageProvider")

private fun setQueryHandler(request: HTMLEditorProvider.Request, handler: HTMLEditorProvider.JsQueryHandler?, origins: List<String>) {
  val method = providerClass.getDeclaredMethod("setQueryHandler", HTMLEditorProvider.Request::class.java,
                                               HTMLEditorProvider.JsQueryHandler::class.java, List::class.java)
  method.isAccessible = true
  method.invoke(null, request, handler, origins)
}

// The module of the provider is only a runtime dependency of the tests.
@Suppress("UNCHECKED_CAST")
private fun trustedOrigins(url: String, webSiteUrl: String?): List<String> {
  val method = providerClass.getDeclaredMethod("trustedOrigins", String::class.java, String::class.java)
  method.isAccessible = true
  return method.invoke(null, url, webSiteUrl) as List<String>
}
