// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/** Returns the form text of a body that [ITNProxy.createRequest] builds. */
internal fun unpackReportForm(body: BufferExposingByteArrayOutputStream): String =
  GZIPInputStream(ByteArrayInputStream(body.internalBuffer, 0, body.size()))
    .readBytes()
    .toString(StandardCharsets.UTF_8)

/** Returns the decoded key-value pairs of the form text, in the order of the text. A key can occur more than once. */
internal fun parseReportForm(text: String): List<Pair<String, String>> =
  text.split("&").map { pair ->
    URLDecoder.decode(pair.substringBefore('='), StandardCharsets.UTF_8) to
      URLDecoder.decode(pair.substringAfter('=', ""), StandardCharsets.UTF_8)
  }
