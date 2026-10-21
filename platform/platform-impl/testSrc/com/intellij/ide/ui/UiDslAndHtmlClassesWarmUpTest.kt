// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
internal class UiDslAndHtmlClassesWarmUpTest {
  @Test
  fun `every class of the warm-up exists`() {
    val classLoader = UiDslAndHtmlClassesWarmUp::class.java.classLoader
    // a renamed class silently drops out of the warm-up, so the list must follow the code
    val missingClassNames = UI_DSL_AND_HTML_CLASS_NAMES.filter { className ->
      runCatching { Class.forName(className, false, classLoader) }.isFailure
    }

    assertThat(missingClassNames).isEmpty()
  }

  @Test
  fun `the warm-up runs on a thread that is not the EDT`() {
    assertThat(SwingUtilities.isEventDispatchThread()).isFalse()

    warmUpUiDslAndHtmlClasses()
  }
}
