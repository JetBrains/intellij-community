// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(LowLevelLocalMachineAccess::class)

package com.intellij.util.system

import org.jetbrains.annotations.ApiStatus
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout

/**
 * The glibc version of the local machine, read once with a `confstr` downcall.
 *
 * This probe lives here, and not beside [OS], because `intellij.platform.util` stays at Java 8 and cannot use
 * the Foreign Function and Memory API.
 */
@ApiStatus.Internal
object GlibcVersion {
  /** The glibc version, for example `2.39`. `null` on a machine that does not run Linux, and when the probe fails. */
  val current: String? by lazy {
    if (OS.CURRENT != OS.Linux) null else runCatching { readGlibcVersion() }.getOrNull()
  }
}

private const val PREFIX = "glibc "

/** `_CS_GNU_LIBC_VERSION` from `unistd.h`. */
private const val CS_GNU_LIBC_VERSION = 2

private const val BUFFER_SIZE = 64L

/**
 * Calls `size_t confstr(int name, char *buf, size_t len)`.
 *
 * `confstr` writes a NUL-terminated string and returns the byte count it needs, the terminator included.
 * It returns `0` when the name has no value. Every supported Linux target is 64-bit, so `size_t` is 8 bytes.
 */
@LowLevelLocalMachineAccess
private fun readGlibcVersion(): String? {
  val linker = Linker.nativeLinker()
  val sizeT: MemoryLayout = linker.canonicalLayouts()["size_t"]!!
  val confstr = linker.downcallHandle(
    linker.defaultLookup().findOrThrow("confstr"),
    FunctionDescriptor.of(sizeT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, sizeT),
  )
  Arena.ofConfined().use { arena ->
    val buffer = arena.allocate(BUFFER_SIZE)
    val needed = confstr.invokeExact(CS_GNU_LIBC_VERSION, buffer, BUFFER_SIZE) as Long
    if (needed <= PREFIX.length) {
      return null
    }
    val value = buffer.getString(0)
    return if (value.startsWith(PREFIX)) value.substring(PREFIX.length) else null
  }
}
