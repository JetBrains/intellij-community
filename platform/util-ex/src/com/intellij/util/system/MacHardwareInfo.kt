// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import org.jetbrains.annotations.ApiStatus

/**
 * Best-effort, cached hardware characteristics of the local machine.
 *
 * The value is gathered via a lightweight native probe, [Sysctl]. It lives here, and not in `intellij.platform.util`,
 * because that module stays at Java 8 and cannot use the Foreign Function and Memory API.
 */
@ApiStatus.Internal
object MacHardwareInfo {
  private const val MACBOOK_NEO_MODEL_ID = "Mac17,5"

  /** `true` when running on a MacBook Neo (`hw.model` == `Mac17,5`); `null` on non-macOS machines. */
  val isMacbookNeo: Boolean? by lazy { modelIdentifier?.let { it == MACBOOK_NEO_MODEL_ID } }

  /** Hardware model identifier on macOS, e.g. `Mac17,5` or `MacBookPro14,3`; `null` on other OSes or when unavailable. */
  private val modelIdentifier: String? by lazy {
    if (OS.CURRENT != OS.macOS) return@lazy null
    val raw = runCatching { Sysctl.stringByName("hw.model") }.getOrNull() ?: return@lazy null
    raw.trim().ifEmpty { null }
  }
}
