// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.generateLongString
import org.junit.Test
import kotlin.test.assertTrue

class IntTextFieldTest {

  @Test
  fun testPreferredSize() {
    val panel = panel {
      row("Label:") {
        intTextField()
          .text(generateLongString())
      }
    }

    assertTrue(panel.preferredSize.width < 300)
  }
}
