// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.CharsetToolkit
import java.nio.charset.StandardCharsets

abstract class ExecutionExceptionWithAttachments : ExecutionException, ExceptionWithAttachments {
  val rawStdout: ByteArray?
  val rawStderr: ByteArray?
  val stdout: @NlsSafe String
  val stderr: @NlsSafe String

  constructor(@NlsContexts.DialogMessage s: String, rawStdout: ByteArray?, rawStderr: ByteArray?) : super(s) {
    this.rawStdout = rawStdout
    this.rawStderr = rawStderr
    stdout = decode(rawStdout)
    stderr = decode(rawStderr)
  }

  constructor(@NlsContexts.DialogMessage s: String, throwable: Throwable, rawStdout: ByteArray?, rawStderr: ByteArray?) : super(s, throwable) {
    this.rawStdout = rawStdout
    this.rawStderr = rawStderr
    stdout = decode(rawStdout)
    stderr = decode(rawStderr)
  }

  constructor(@NlsContexts.DialogMessage s: String, stdout: String?, stderr: String?) : super(s) {
    this.rawStdout = encode(stdout)
    this.rawStderr = encode(stderr)
    this.stdout = stdout ?: ""
    this.stderr = stderr ?: ""
  }

  constructor(@NlsContexts.DialogMessage s: String, throwable: Throwable, stdout: String?, stderr: String?) : super(s, throwable) {
    this.rawStdout = encode(stdout)
    this.rawStderr = encode(stderr)
    this.stdout = stdout ?: ""
    this.stderr = stderr ?: ""
  }

  override fun getAttachments(): Array<Attachment> =
    // Process outputs can contain private data, not sending them by default.
    listOfNotNull(
      rawStdout?.let { Attachment("stdout.txt", it, stdout).apply { isIncluded = false } },
      rawStderr?.let { Attachment("stderr.txt", it, stderr).apply { isIncluded = false } },
    ).toTypedArray()

  companion object {
    @JvmStatic
    private fun decode(source: ByteArray?): String =
      source?.let { CharsetToolkit.decodeString(it, StandardCharsets.UTF_8) }
      ?: ""

    @JvmStatic
    private fun encode(source: String?): ByteArray? =
      source?.toByteArray(StandardCharsets.UTF_8)
  }
}