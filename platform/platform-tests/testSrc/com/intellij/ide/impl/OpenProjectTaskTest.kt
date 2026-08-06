// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class OpenProjectTaskTest {
  @Test
  fun `implementation options preserve their payload and file marker`() {
    val originalImplOptions = Any()
    val replacementImplOptions = Any()
    val options = OpenProjectTask {
      implOptions = originalImplOptions
      opensFileAfterProjectOpen = true
    }

    assertThat(options.implOptions).isNotSameAs(originalImplOptions)
    assertThat(options.effectiveImplOptions).isSameAs(originalImplOptions)
    assertThat(options.opensFileAfterProjectOpen).isTrue()

    val copiedOptions = options.copy(projectName = "copied")
    assertThat(copiedOptions.effectiveImplOptions).isSameAs(originalImplOptions)
    assertThat(copiedOptions.opensFileAfterProjectOpen).isTrue()

    val replacedOptions = copiedOptions.withImplOptions(replacementImplOptions)
    assertThat(replacedOptions.effectiveImplOptions).isSameAs(replacementImplOptions)
    assertThat(replacedOptions.opensFileAfterProjectOpen).isTrue()

    val defaultOptions = OpenProjectTask()
    assertThat(defaultOptions.implOptions).isNotNull()
    assertThat(defaultOptions.effectiveImplOptions).isNull()
    assertThat(defaultOptions.opensFileAfterProjectOpen).isFalse()
  }
}
