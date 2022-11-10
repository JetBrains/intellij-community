// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.UiDslException
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class RowTest {
  @Test
  fun testHorizontalAlignForWordWrap() {
    assertThrows<UiDslException> {
      panel {
        row {
          comment("Comment")
            .align(Align.CENTER)
        }
      }
    }

    assertThrows<UiDslException> {
      panel {
        row {
          text("Text")
            .align(AlignX.RIGHT)
        }
      }
    }

    panel {
      row {
        text("Text")
          .align(AlignY.CENTER)
      }
    }
  }
}