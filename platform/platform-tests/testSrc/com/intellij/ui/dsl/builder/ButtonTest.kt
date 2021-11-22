// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.layout.*
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class ButtonTest {

  var property = true

  @Test
  fun testBindingInitialization() {
    property = Random.Default.nextBoolean()
    panel {
      row {
        val checkBox = checkBox("")
          .bindSelected(::property)
        assertEquals(checkBox.component.isSelected, property)
      }
    }

    var localProperty = Random.Default.nextBoolean()
    panel {
      row {
        val checkBox = checkBox("")
          .bindSelected({ localProperty }, { localProperty = it })
        assertEquals(checkBox.component.isSelected, localProperty)
      }
    }

    panel {
      row {
        val checkBox = checkBox("")
          .bindSelected(PropertyBinding({ localProperty }, { localProperty = it }))
        assertEquals(checkBox.component.isSelected, localProperty)
      }
    }
  }
}