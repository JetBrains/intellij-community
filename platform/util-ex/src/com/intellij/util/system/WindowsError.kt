// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.system

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/** The Windows error codes as text. */
@ApiStatus.Internal
object WindowsError {
  /**
   * Formats a Windows error code as an `HRESULT`.
   *
   * A `GetLastError` code is a positive `DWORD`. The function maps it into the `FACILITY_WIN32` range, as
   * `HRESULT_FROM_WIN32` does. A code that is already an `HRESULT` or an `NTSTATUS` is negative, and passes unchanged.
   *
   * @return the code as `0x` and eight hexadecimal digits
   */
  @ApiStatus.Internal
  fun prettyHRESULT(lastError: Int): String {
    val hResult = if (lastError <= 0) {
      lastError
    }
    else {
      (lastError and 0x0000FFFF) or (7 shl 16) or 0x80000000.toInt()
    }

    return String.format("0x%08X", hResult)
  }
}

/**
 * Builds a failed [Result] for a Windows call.
 *
 * @param code the `GetLastError` code that the failing call captured, or the `NTSTATUS` that it answered
 */
internal fun <T> winFailure(message: @NlsSafe String, code: Int): Result<T> =
  Result.failure(WindowsException("$message: ${WindowsError.prettyHRESULT(code)}"))

private class WindowsException(message: @NlsSafe String) : Exception(message)
