// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

/**
 * The attachments in the body of a report. A report over the size limit skips its JFR snapshot. See IJPL-258231.
 */
@TestApplication
internal class ITNProxyRequestTest {
  private class Request(val sentSize: Int, val unpackedSize: Int, val form: List<Pair<String, String>>) {
    /** The attachment names and contents, in the order of the body. */
    val attachments: List<Pair<String, ByteArray>> =
      form.filter { it.first == "attachment.name" }.map { it.second }
        .zip(form.filter { it.first == "attachment.value" }.map { Base64.getDecoder().decode(it.second) })

    val kept: List<String> = attachments.map { it.first } - ITNProxy.SKIPPED_ATTACHMENTS_NAME

    val skippedNote: String? = attachments.firstOrNull { it.first == ITNProxy.SKIPPED_ATTACHMENTS_NAME }?.second?.toString(StandardCharsets.UTF_8)

    val skipped: List<String> = skippedNote?.lines()?.filter(String::isNotEmpty)?.map { it.substringBefore(':') }.orEmpty()
  }

  @Test
  fun `a report writes each attachment as a name and a Base64 value, in the order of the event`() {
    // the binary attachment spans several chunks of the writer
    val binary = Random(7).nextBytes(20_000)
    val request = request(listOf(Attachment("first.txt", "text"), Attachment("data.bin", binary, "data"), Attachment("empty.txt", "")))

    val keys = request.form.map { it.first }
    val start = keys.indexOf("attachment.name")
    assertThat(keys.subList(start, start + 6)).containsExactly(
      "attachment.name", "attachment.value", "attachment.name", "attachment.value", "attachment.name", "attachment.value",
    )
    assertThat(request.kept).containsExactly("first.txt", "data.bin", "empty.txt")
    assertThat(request.attachments[0].second.toString(StandardCharsets.UTF_8)).isEqualTo("text")
    assertThat(request.attachments[1].second).isEqualTo(binary)
    assertThat(request.attachments[2].second).isEmpty()
    assertThat(request.skippedNote).isNull()
  }

  @Test
  fun `a report over the limit skips its JFR snapshot and keeps the rest`() {
    val request = request(listOf(jfrAttachment(200 * 1024), textAttachment("dump-1.txt", 1024)), maxSize = MAX_SIZE)

    assertThat(request.kept).containsExactly("dump-1.txt")
    assertThat(request.skipped).containsExactly("snapshot.jfr")
    assertThat(request.sentSize).isLessThanOrEqualTo(MAX_SIZE.toInt())
  }

  @Test
  fun `a report over the limit without a JFR snapshot goes as is`() {
    val request = request(listOf(textAttachment("dump-1.txt", 1024), randomAttachment("dump-2.txt", 200 * 1024)), maxSize = MAX_SIZE)

    assertThat(request.kept).containsExactly("dump-1.txt", "dump-2.txt")
    assertThat(request.skippedNote).isNull()
    assertThat(request.sentSize).isGreaterThan(MAX_SIZE.toInt())
  }

  @Test
  fun `a report keeps a JFR snapshot that fits the limit`() {
    val request = request(listOf(textAttachment("dump-1.txt", 1024), jfrAttachment(10 * 1024)), maxSize = MAX_SIZE)

    assertThat(request.kept).containsExactly("dump-1.txt", "snapshot.jfr")
    assertThat(request.skippedNote).isNull()
  }

  @Test
  fun `a report keeps an attachment that fits the limit after gzip`() {
    val request = request(listOf(textAttachment("report.txt", 120 * 1024)), maxSize = MAX_SIZE)

    assertThat(request.unpackedSize).isGreaterThan(MAX_SIZE.toInt())
    assertThat(request.kept).containsExactly("report.txt")
    assertThat(request.skippedNote).isNull()
  }

  @Test
  fun `a text-only report keeps the text attachments and names the others`() {
    val request = request(listOf(textAttachment("dump-1.txt", 1024), jfrAttachment(1024)), maxSize = MAX_SIZE, textOnly = true)

    assertThat(request.kept).containsExactly("dump-1.txt")
    assertThat(request.skippedNote).isEqualTo("snapshot.jfr: skipped, the server rejected the size of the full report\n")
  }

  @Test
  fun `a canceled report stops building its request`() {
    val job = Job().apply { cancel() }
    assertThrows<CancellationException> {
      // a coroutine installs its job in the thread context in the same way
      installThreadContext(job) {
        request(listOf(jfrAttachment(100 * 1024)))
      }
    }
  }

  private fun request(
    attachments: List<Attachment>,
    maxSize: Long = Long.MAX_VALUE,
    textOnly: Boolean = false,
  ): Request {
    val event = IdeaLoggingEvent("message", RuntimeException("boom"), attachments, null, null, UnhandledExceptionKind.HANDLED)
    val bean = ITNProxy.ErrorBean(event, null, null, null, null, null, false)
    val body = ITNProxy.createRequest(event, bean, maxSize = maxSize, textOnly = textOnly)
    val text = unpackReportForm(body)
    val request = Request(body.size(), text.length, parseReportForm(text))
    assertThat(request.form.count { it.first == "attachment.value" }).isEqualTo(request.form.count { it.first == "attachment.name" })
    return request
  }

  private fun textAttachment(name: String, size: Int): Attachment = Attachment(name, "a".repeat(size))

  private fun jfrAttachment(size: Int): Attachment = randomAttachment("snapshot.jfr", size)

  /** Random bytes do not compress, so the gzipped share of the attachment is close to its size. */
  private fun randomAttachment(name: String, size: Int): Attachment = Attachment(name, Random(42).nextBytes(size), name)

  private companion object {
    const val MAX_SIZE = 128L * 1024
  }
}
