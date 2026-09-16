// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.system.WindowsSystemLibraries
import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemoryLayout.PathElement.groupElement
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets

private val LOG = logger<WinHttp>()

/**
 * The proxy settings that WinHTTP reports, from downcalls into `winhttp.dll`. Windows only: the first call loads the DLL.
 *
 * WinHTTP returns each string in global memory. Each reader copies the string and releases it with `GlobalFree`.
 * Each reader returns `null` when the call fails, and logs the `GetLastError` code at the debug level.
 */
@ApiStatus.Internal
object WinHttp {
  /** `WINHTTP_ACCESS_TYPE_NO_PROXY`: WinHTTP resolves every host directly. */
  const val ACCESS_TYPE_NO_PROXY: Int = 1

  /** `ERROR_WINHTTP_AUTODETECTION_FAILED`: neither DHCP nor DNS publishes a PAC URL. This is the normal result on most networks. */
  private const val ERROR_WINHTTP_AUTODETECTION_FAILED = 12180

  /** `WINHTTP_AUTO_DETECT_TYPE_DHCP | WINHTTP_AUTO_DETECT_TYPE_DNS_A` */
  private const val AUTO_DETECT_FLAGS = 0x1 or 0x2

  /** `WINHTTP_PROXY_INFO`: the machine-wide settings that `netsh winhttp set proxy` writes. */
  data class DefaultProxyConfig(val accessType: Int, val proxy: String?, val proxyBypass: String?)

  /** `WINHTTP_CURRENT_USER_IE_PROXY_CONFIG`: the Internet Options of the current user. */
  data class UserProxyConfig(val autoDetect: Boolean, val autoConfigUrl: String?, val proxy: String?, val proxyBypass: String?)

  /** Calls `WinHttpGetDefaultProxyConfiguration`. */
  fun defaultProxyConfig(): DefaultProxyConfig? {
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Handles.CALL_STATE)
      val info = arena.allocate(Handles.PROXY_INFO)
      val succeeded = Handles.GET_DEFAULT_PROXY_CONFIGURATION.invokeExact(callState, info) as Int
      if (succeeded == 0) {
        LOG.debug { "WinHttpGetDefaultProxyConfiguration: ${lastError(callState)}" }
        return null
      }
      return DefaultProxyConfig(
        accessType = info.get(JAVA_INT, Handles.PROXY_INFO.byteOffset(groupElement("dwAccessType"))),
        proxy = takeGlobalString(info.get(ADDRESS, Handles.PROXY_INFO.byteOffset(groupElement("lpszProxy")))),
        proxyBypass = takeGlobalString(info.get(ADDRESS, Handles.PROXY_INFO.byteOffset(groupElement("lpszProxyBypass")))),
      )
    }
  }

  /** Calls `WinHttpGetIEProxyConfigForCurrentUser`. */
  fun currentUserProxyConfig(): UserProxyConfig? {
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Handles.CALL_STATE)
      val config = arena.allocate(Handles.IE_PROXY_CONFIG)
      val succeeded = Handles.GET_IE_PROXY_CONFIG_FOR_CURRENT_USER.invokeExact(callState, config) as Int
      if (succeeded == 0) {
        LOG.debug { "WinHttpGetIEProxyConfigForCurrentUser: ${lastError(callState)}" }
        return null
      }
      return UserProxyConfig(
        autoDetect = config.get(JAVA_INT, Handles.IE_PROXY_CONFIG.byteOffset(groupElement("fAutoDetect"))) != 0,
        autoConfigUrl = takeGlobalString(config.get(ADDRESS, Handles.IE_PROXY_CONFIG.byteOffset(groupElement("lpszAutoConfigUrl")))),
        proxy = takeGlobalString(config.get(ADDRESS, Handles.IE_PROXY_CONFIG.byteOffset(groupElement("lpszProxy")))),
        proxyBypass = takeGlobalString(config.get(ADDRESS, Handles.IE_PROXY_CONFIG.byteOffset(groupElement("lpszProxyBypass")))),
      )
    }
  }

  /**
   * Calls `WinHttpDetectAutoProxyConfigUrl` with the DHCP and DNS methods. The call blocks until the network answers or times out.
   *
   * @return the PAC URL that WPAD publishes, or `null` when there is none
   */
  fun detectAutoProxyConfigUrl(): String? {
    Arena.ofConfined().use { arena ->
      val callState = arena.allocate(Handles.CALL_STATE)
      val urlPointer = arena.allocate(ADDRESS)
      val succeeded = Handles.DETECT_AUTO_PROXY_CONFIG_URL.invokeExact(callState, AUTO_DETECT_FLAGS, urlPointer) as Int
      if (succeeded == 0) {
        val error = lastError(callState)
        if (error != ERROR_WINHTTP_AUTODETECTION_FAILED) {
          LOG.debug { "WinHttpDetectAutoProxyConfigUrl: $error" }
        }
        return null
      }
      return takeGlobalString(urlPointer.get(ADDRESS, 0))
    }
  }

  private fun lastError(callState: MemorySegment): Int = callState.get(JAVA_INT, Handles.CALL_STATE.byteOffset(groupElement("GetLastError")))

  /** Copies an `LPWSTR` that lives in global memory, then releases it. */
  private fun takeGlobalString(pointer: MemorySegment): String? {
    if (pointer.address() == 0L) {
      return null
    }
    try {
      return pointer.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_16LE)
    }
    finally {
      val unreleased = Handles.GLOBAL_FREE.invokeExact(pointer) as MemorySegment
      if (unreleased.address() != 0L) {
        LOG.debug("GlobalFree failed")
      }
    }
  }

  /** `BOOL` and `DWORD` are `int`, `LPWSTR` and `HGLOBAL` are addresses. The layouts are for a 64-bit target: a pointer follows a 4-byte field after 4 bytes of padding. */
  private object Handles {
    private val LINKER: Linker = Linker.nativeLinker()
    private val WINHTTP: SymbolLookup = WindowsSystemLibraries.lookup("winhttp.dll")
    private val KERNEL32: SymbolLookup = WindowsSystemLibraries.lookup("kernel32.dll")
    private val CAPTURE_LAST_ERROR: Linker.Option = Linker.Option.captureCallState("GetLastError")

    val CALL_STATE: StructLayout = Linker.Option.captureStateLayout()

    /** `WINHTTP_PROXY_INFO { DWORD dwAccessType; LPWSTR lpszProxy; LPWSTR lpszProxyBypass; }` */
    val PROXY_INFO: StructLayout = MemoryLayout.structLayout(
      JAVA_INT.withName("dwAccessType"),
      MemoryLayout.paddingLayout(4),
      ADDRESS.withName("lpszProxy"),
      ADDRESS.withName("lpszProxyBypass"),
    )

    /** `WINHTTP_CURRENT_USER_IE_PROXY_CONFIG { BOOL fAutoDetect; LPWSTR lpszAutoConfigUrl; LPWSTR lpszProxy; LPWSTR lpszProxyBypass; }` */
    val IE_PROXY_CONFIG: StructLayout = MemoryLayout.structLayout(
      JAVA_INT.withName("fAutoDetect"),
      MemoryLayout.paddingLayout(4),
      ADDRESS.withName("lpszAutoConfigUrl"),
      ADDRESS.withName("lpszProxy"),
      ADDRESS.withName("lpszProxyBypass"),
    )

    /** `BOOL WinHttpGetDefaultProxyConfiguration(WINHTTP_PROXY_INFO*)` */
    val GET_DEFAULT_PROXY_CONFIGURATION: MethodHandle = LINKER.downcallHandle(
      WINHTTP.findOrThrow("WinHttpGetDefaultProxyConfiguration"), FunctionDescriptor.of(JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR)

    /** `BOOL WinHttpGetIEProxyConfigForCurrentUser(WINHTTP_CURRENT_USER_IE_PROXY_CONFIG*)` */
    val GET_IE_PROXY_CONFIG_FOR_CURRENT_USER: MethodHandle = LINKER.downcallHandle(
      WINHTTP.findOrThrow("WinHttpGetIEProxyConfigForCurrentUser"), FunctionDescriptor.of(JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR)

    /** `BOOL WinHttpDetectAutoProxyConfigUrl(DWORD flags, LPWSTR* url)` */
    val DETECT_AUTO_PROXY_CONFIG_URL: MethodHandle = LINKER.downcallHandle(
      WINHTTP.findOrThrow("WinHttpDetectAutoProxyConfigUrl"), FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS), CAPTURE_LAST_ERROR)

    /** `HGLOBAL GlobalFree(HGLOBAL)`: returns `NULL` on success */
    val GLOBAL_FREE: MethodHandle = LINKER.downcallHandle(KERNEL32.findOrThrow("GlobalFree"), FunctionDescriptor.of(ADDRESS, ADDRESS))
  }
}
