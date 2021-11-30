// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.UiDslException
import org.junit.Test
import org.junit.jupiter.api.assertThrows

class ButtonsGroupTest {

  var bindValue = 1

  @Test
  fun testInvalidGroups() {
    assertThrows<UiDslException> {
      panel {
        buttonsGroup {
          row {
            radioButton("", 1)
          }
        }
      }
    }

    panel {
      buttonsGroup {
        row {
          radioButton("", 1)
        }
      }.bind(::bindValue)
    }

    assertThrows<UiDslException> {
      panel {
        buttonsGroup {
          row {
            radioButton("", true)
          }
        }.bind(::bindValue)
      }
    }

    assertThrows<UiDslException> {
      panel {
        buttonsGroup {
          row {
            radioButton("")
          }
        }.bind(::bindValue)
      }
    }
  }
}
