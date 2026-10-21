// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
internal class FrameClassesWarmUpTest {
  private val classLoader = FrameClassesWarmUp::class.java.classLoader

  @Test
  fun `every class of the warm-up exists`() {
    val missing = (COMMON_FRAME_CLASS_NAMES + MAC_FRAME_CLASS_NAMES + OTHER_OS_FRAME_CLASS_NAMES).filter { name ->
      classLoader.getResource(name.replace('.', '/') + ".class") == null
    }
    assertThat(missing).isEmpty()
  }

  @Test
  fun `every class is listed once and every load-only class is listed`() {
    val all = COMMON_FRAME_CLASS_NAMES + MAC_FRAME_CLASS_NAMES + OTHER_OS_FRAME_CLASS_NAMES
    assertThat(all).doesNotHaveDuplicates()
    assertThat(all).containsAll(LOAD_ONLY_FRAME_CLASS_NAMES)
  }

  @Test
  fun `the classes that the warm-up initializes initialize off the EDT`() {
    assertThat(SwingUtilities.isEventDispatchThread()).isFalse()
    val failed = frameClassNamesOfCurrentOs().filter { it !in LOAD_ONLY_FRAME_CLASS_NAMES }.mapNotNull { name ->
      runCatching { Class.forName(name, true, classLoader) }.exceptionOrNull()?.let { "$name: $it" }
    }
    assertThat(failed).isEmpty()
  }
}
