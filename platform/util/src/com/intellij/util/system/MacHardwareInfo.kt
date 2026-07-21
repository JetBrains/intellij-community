// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import com.intellij.jna.JnaLoader
import com.sun.jna.Memory
import com.sun.jna.platform.mac.SystemB
import com.sun.jna.platform.unix.LibCAPI.size_t
import org.jetbrains.annotations.ApiStatus

/**
 * Best-effort, cached hardware characteristics of the local machine.
 *
 * The value is gathered via a lightweight native probe, guarded by [JnaLoader.isLoaded].
 */
@ApiStatus.Internal
object MacHardwareInfo {
  private const val MACBOOK_NEO_MODEL_ID = "Mac17,5"

  /** `true` when running on a MacBook Neo (`hw.model` == `Mac17,5`); `null` on non-macOS machines. */
  val isMacbookNeo: Boolean? by lazy { modelIdentifier?.let { it == MACBOOK_NEO_MODEL_ID } }

  /** Hardware model identifier on macOS, e.g. `Mac17,5` or `MacBookPro14,3`; `null` on other OSes or when unavailable. */
  private val modelIdentifier: String? by lazy {
    if (OS.CURRENT != OS.macOS) return@lazy null
    val raw = jna { macSysctlString("hw.model") } ?: return@lazy null
    raw.trim().ifEmpty { null }
  }

  private inline fun <T> jna(block: () -> T?): T? =
    if (JnaLoader.isLoaded()) runCatching(block).getOrNull() else null

  /** Reads a string sysctl value via JNA, mirroring the `SystemB.sysctlbyname` pattern used by [CpuArch]. */
  @Suppress("SameParameterValue")
  private fun macSysctlString(name: String): String? {
    val sizeRef = size_t.ByReference()
    if (SystemB.INSTANCE.sysctlbyname(name, null, sizeRef, null, size_t.ZERO) != 0) return null
    val size = sizeRef.value.toInt()
    if (size <= 1) return null
    val buffer = Memory(size.toLong())
    if (SystemB.INSTANCE.sysctlbyname(name, buffer, sizeRef, null, size_t.ZERO) != 0) return null
    return buffer.getString(0)
  }
}
