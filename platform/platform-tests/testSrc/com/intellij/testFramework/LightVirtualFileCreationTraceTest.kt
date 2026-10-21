// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.util.StackTraceSnapshot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The creation trace of a light file keeps the top frames of the code that creates the file, below the constructors of the file,
 * and it keeps no class loader of that code. A light file of another plugin can outlive the plugin that created it.
 */
internal class LightVirtualFileCreationTraceTest {
  @Test
  fun `a creation trace keeps no class loader of the code that creates the file`() {
    val call = callUnderOwnClassLoader { LightVirtualFileBase.captureCreationTrace(LightVirtualFile::class.java) }

    call.loader.ensureCollected()
    assertThat(call.result).isInstanceOf(StackTraceSnapshot::class.java)
  }

  @Test
  fun `a creation trace starts at the code that creates the file, below its constructors, and keeps 16 frames`() {
    val frames = (createFile().creationTrace as StackTraceSnapshot).toThrowable().stackTrace

    assertThat(frames.first().methodName).isEqualTo("createFile")
    assertThat(frames).hasSize(16)
  }

  private fun createFile(): TracedLightFile = TracedLightFile()
}

/** A light file that takes its creation trace in its constructor, as LightVirtualFileBase does outside the unit test mode. */
private open class TracedLightFileBase : LightVirtualFile("traced.txt", "") {
  val creationTrace: Any = captureCreationTrace(javaClass)
}

/** A subclass, so the constructors of two light file classes are on the stack of the capture. */
private class TracedLightFile : TracedLightFileBase()
