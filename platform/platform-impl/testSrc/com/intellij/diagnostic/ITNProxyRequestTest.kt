// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.UnhandledExceptionKind
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.random.Random

/**
 * The attachments in the body of a report.
 */
@TestApplication
internal class ITNProxyRequestTest {
  private class Request(val form: List<Pair<String, String>>) {
    /** The attachment names and contents, in the order of the body. */
    val attachments: List<Pair<String, ByteArray>> =
      form.filter { it.first == "attachment.name" }.map { it.second }
        .zip(form.filter { it.first == "attachment.value" }.map { Base64.getDecoder().decode(it.second) })

    val kept: List<String> = attachments.map { it.first }
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
  }

  private fun request(attachments: List<Attachment>): Request {
    val event = IdeaLoggingEvent("message", RuntimeException("boom"), attachments, null, null, UnhandledExceptionKind.HANDLED)
    val bean = ITNProxy.ErrorBean(event, null, null, null, null, null, false)
    val request = Request(parseReportForm(unpackReportForm(ITNProxy.createRequest(event, bean))))
    assertThat(request.form.count { it.first == "attachment.value" }).isEqualTo(request.form.count { it.first == "attachment.name" })
    return request
  }
}
