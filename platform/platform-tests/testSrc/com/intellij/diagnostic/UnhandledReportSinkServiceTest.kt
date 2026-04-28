// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.plugins.TestIdeaPluginDescriptor
import com.intellij.openapi.diagnostic.ErrorReportSink
import com.intellij.openapi.diagnostic.ErrorReportSinkBean
import com.intellij.openapi.diagnostic.UnhandledErrorReport
import com.intellij.openapi.diagnostic.UnhandledExceptionReport
import com.intellij.openapi.diagnostic.UnhandledFreezeReport
import com.intellij.openapi.diagnostic.UnhandledReportSinkService
import com.intellij.openapi.diagnostic.UnhandledReportSinkService.PluginExceptionReportData
import com.intellij.openapi.diagnostic.UnhandledReportSinkService.PluginFreezeReportData
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class UnhandledReportSinkServiceTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @get:Rule
  val disposableRule = DisposableRule()

  private val testPluginId = PluginId.getId("com.test.errorsink.testplugin")

  class TestSink : ErrorReportSink {
    val reportDeferred = CompletableDeferred<UnhandledErrorReport>()

    override suspend fun submit(report: UnhandledErrorReport) {
      reportDeferred.complete(report)
    }
  }

  private fun registerTestErrorSink(): TestSink {
    val bean = ErrorReportSinkBean().apply {
      implementation = TestSink::class.java.name
    }
    bean.pluginDescriptor = object : TestIdeaPluginDescriptor() {
      override fun getPluginId() = testPluginId
      override fun getPluginClassLoader(): ClassLoader? = null
    }
    ErrorReportSinkBean.EP_NAME.point.registerExtension(bean, disposableRule.disposable)
    return bean.instance as TestSink
  }

  @Test
  fun exceptionIsReportedToSink() = timeoutRunBlocking {
    val testSink = registerTestErrorSink()

    val service = UnhandledReportSinkService.getInstance()!!
    service.report(PluginExceptionReportData(testPluginId, RuntimeException("test exception")))

    val report = withTimeout(5_000.milliseconds) { testSink.reportDeferred.await() }
    assertTrue(report is UnhandledExceptionReport)
    assertEquals(RuntimeException::class.java, (report as UnhandledExceptionReport).exceptionClass)
  }

  @Test
  fun freezeIsReportedToSink() = timeoutRunBlocking {
    val testSink = registerTestErrorSink()

    val service = UnhandledReportSinkService.getInstance()!!
    val freezeMessage = "UI freeze"
    val durationMs = 3_000L
    service.report(
      PluginFreezeReportData(
        pluginId = testPluginId,
        message = freezeMessage,
        durationMs = durationMs,
        attachments = emptyList(),
        threadDumps = emptyList(),
      )
    )

    val report = withTimeout(5_000.milliseconds) { testSink.reportDeferred.await() }
    assertTrue(report is UnhandledFreezeReport)
    report as UnhandledFreezeReport
    assertEquals(freezeMessage, report.message)
    assertEquals(durationMs, report.durationMs)
  }
}
