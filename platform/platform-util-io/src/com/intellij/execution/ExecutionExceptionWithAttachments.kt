// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.NonExtendable
import java.nio.charset.StandardCharsets

/**
 * The purpose of this class is to send additional data to Exception Analyzer, which is a company-private service.
 * Third party developers can barely benefit from using this class.
 */
@NonExtendable
abstract class ExecutionExceptionWithAttachments : ExecutionException, ExceptionWithAttachments {
  @Internal
  val stdout: @NlsSafe String

  @Internal
  val stderr: @NlsSafe String

  @Internal
  constructor(@NlsContexts.DialogMessage s: String?, rawStdout: ByteArray?, rawStderr: ByteArray?) : super(s) {
    stdout = decode(rawStdout)
    stderr = decode(rawStderr)
  }

  @Internal
  constructor(@NlsContexts.DialogMessage s: String?, throwable: Throwable, rawStdout: ByteArray?, rawStderr: ByteArray?) :
    super(s, throwable) {
    stdout = decode(rawStdout)
    stderr = decode(rawStderr)
  }

  @Internal
  constructor(@NlsContexts.DialogMessage s: String?, stdout: String?, stderr: String?) : super(s) {
    this.stdout = stdout ?: ""
    this.stderr = stderr ?: ""
  }

  @Internal
  constructor(@NlsContexts.DialogMessage s: String?, throwable: Throwable, stdout: String?, stderr: String?) : super(s, throwable) {
    this.stdout = stdout ?: ""
    this.stderr = stderr ?: ""
  }

  @Internal
  override fun getAttachments(): Array<Attachment> =
    // Process outputs can contain private data, not sending them by default.
    listOfNotNull(
      stdout.takeIf { it.isNotBlank() }?.let { Attachment("stdout.txt", it).apply { isIncluded = false } },
      stderr.takeIf { it.isNotBlank() }?.let { Attachment("stderr.txt", it).apply { isIncluded = false } },
    ).toTypedArray()

  private companion object {
    @JvmStatic
    private fun decode(source: ByteArray?): String =
      source?.let { CharsetToolkit.decodeString(it, StandardCharsets.UTF_8) }
      ?: ""

    @JvmStatic
    private fun encode(source: String?): ByteArray? =
      source?.toByteArray(StandardCharsets.UTF_8)
  }
}