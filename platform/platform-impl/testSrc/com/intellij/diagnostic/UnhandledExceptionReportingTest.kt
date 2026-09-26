// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UnhandledExceptionLoggingMode
import com.intellij.openapi.application.impl.processUnhandledException
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.UnhandledException
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * An [UnhandledException] must not reach a report, but its kind must. See IJPL-254578 and IJPL-100.
 */
@TestApplication
internal class UnhandledExceptionReportingTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  private class TestCause(message: String) : RuntimeException(message)

  @Test
  fun `FUS reports the real cause and the kind`() {
    val events = collect { LifecycleUsageTriggerCollector.onError(null, TestCause("boom"), UnhandledExceptionKind.INTERACTIVE, null) }

    assertThat(events).singleElement().satisfies({
      assertThat(it.event.data["error"]).isEqualTo(TestCause::class.java.name)
      assertThat(it.event.data["unhandled_exception_interactive"]).isEqualTo(true)
    })
  }

  @Test
  fun `FUS reports a background unhandled exception`() {
    val events = collect { LifecycleUsageTriggerCollector.onError(null, TestCause("boom"), UnhandledExceptionKind.BACKGROUND, null) }

    assertThat(events).singleElement().satisfies({
      assertThat(it.event.data["unhandled_exception_interactive"]).isEqualTo(false)
    })
  }

  @Test
  fun `FUS adds no field to an exception that was handled`() {
    val events = collect { LifecycleUsageTriggerCollector.onError(null, TestCause("boom"), UnhandledExceptionKind.HANDLED, null) }

    assertThat(events).singleElement().satisfies({
      assertThat(it.event.data).doesNotContainKey("unhandled_exception_interactive")
      assertThat(it.event.data["error"]).isEqualTo(TestCause::class.java.name)
    })
  }

  /** An untouched text keeps the original throwable, and the report shows the real cause. */
  @Test
  fun `a report of a wrapped error carries the real cause`() {
    val cause = TestCause("boom")

    val decoupled = checkNotNull(clusterOf(UnhandledException(cause, true)).decouple()) {
      "`decouple` must not fail on a wrapped throwable"
    }

    assertThat(decoupled.first).isEqualTo("message")
    assertThat(IdeaLoggingEvent(decoupled.first, decoupled.second).throwableText)
      .describedAs("The report must show the real cause")
      .startsWith(TestCause::class.java.name)
  }

  /**
   * A user may edit the stacktrace in the dialog. Then `decouple` matches the text against the class name of the
   * throwable, so that name must also drop the wrapper, or the report can no longer be sent.
   */
  @Test
  fun `a report of a wrapped error survives an edit of its stacktrace`() {
    val cause = TestCause("boom")
    val cluster = clusterOf(UnhandledException(cause, true))
    val shortenedStack = IdeaLogRecordFormatter.formatThrowable(cause).trim().lines().dropLast(1).joinToString("\n")
    cluster.detailsText = "A comment a user typed\n$shortenedStack"

    val decoupled = checkNotNull(cluster.decouple()) { "`decouple` must recover an edited text" }

    assertThat(decoupled.first).isEqualTo("A comment a user typed")
    assertThat(decoupled.second).isInstanceOf(RecoveredThrowable::class.java)
  }

  /**
   * The dialog builds the event from a pool entry, and the entry drops the wrapper.
   * The kind must therefore travel in the event. See IJPL-254578.
   */
  @Test
  fun `a report of an interactive unhandled exception carries the kind`() {
    val form = reportForm(dialogEventOf(UnhandledException(TestCause("interactive"), true)))

    assertThat(form["error.unhandled.interactive"]).isEqualTo("true")
  }

  @Test
  fun `a report of a background unhandled exception carries the kind`() {
    val form = reportForm(dialogEventOf(UnhandledException(TestCause("background"), false)))

    assertThat(form["error.unhandled.interactive"]).isEqualTo("false")
  }

  @Test
  fun `a report of an exception that was handled carries no field`() {
    val form = reportForm(dialogEventOf(TestCause("handled")))

    assertThat(form).doesNotContainKey("error.unhandled.interactive")
  }

  @Test
  fun `a report shows the real cause and never the wrapper`() {
    val form = reportForm(dialogEventOf(UnhandledException(TestCause("boom"), true)))

    assertThat(form.getValue("error.stacktrace")).startsWith(TestCause::class.java.name)
    assertThat(form.getValue("error.stacktrace")).doesNotContain(UnhandledException::class.java.name)
  }

  /** The IDE sends this report without a user. See `ExceptionAutoReportServiceImpl.toLoggingEvent`. */
  @Test
  fun `an automatic report carries the kind`() {
    val entry = entryOf(UnhandledException(TestCause("automatic"), true))

    val event = entry.toLoggingEvent(AbstractMessage.ReportKind.AUTOMATIC, null)

    assertThat(reportForm(event)["error.unhandled.interactive"]).isEqualTo("true")
  }

  /**
   * `Main` can stop the process right after the call, so the log write must not wait for the EDT.
   * See IJPL-254578.
   */
  @Test
  fun `an interactive unhandled exception reaches the log without the EDT`() {
    val cause = TestCause("interactive")

    val logged = LoggedErrorProcessor.executeAndReturnLoggedError {
      processUnhandledException(cause, UnhandledExceptionLoggingMode.Interactive(action = "Test Action"))
    }

    assertThat(logged).describedAs("The log must hold the real cause").isSameAs(cause)
  }

  private fun clusterOf(throwable: Throwable): ErrorMessageCluster =
    ErrorMessageCluster(listOf(entryOf(throwable)), null, null, null)

  private fun entryOf(throwable: Throwable): LogMessage = LogMessage(throwable, "message", emptyList())

  private fun dialogEventOf(throwable: Throwable): IdeaLoggingEvent =
    checkNotNull(clusterOf(throwable).toUserLoggingEvent()) { "A text that no user edited must give an event" }

  private fun reportForm(event: IdeaLoggingEvent): Map<String, String> {
    val bean = ITNProxy.ErrorBean(event, null, null, null, null, null, false)
    val body = ITNProxy.createRequest(event, bean)
    val text = GZIPInputStream(ByteArrayInputStream(body.internalBuffer, 0, body.size()))
      .readBytes()
      .toString(StandardCharsets.UTF_8)
    return text.split("&").associate { pair ->
      URLDecoder.decode(pair.substringBefore('='), StandardCharsets.UTF_8) to
        URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8)
    }
  }

  private fun collect(action: () -> Unit): List<LogEvent> =
    FUCollectorTestCase.collectLogEvents(disposable, action)
      .filter { it.group.id == "lifecycle" && it.event.id == "ide.error" }
}
