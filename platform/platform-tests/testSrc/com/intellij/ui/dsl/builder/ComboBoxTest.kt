// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import org.junit.Test
import kotlin.test.assertEquals

class ComboBoxTest {

  var property = ""

  @Test
  fun testBindingInitialization() {
    val items = (1..9).map { it.toString() }
    property = items.random()
    panel {
      row {
        val comboBox = comboBox(items)
          .bindItem(::property.toNullableProperty())
        assertEquals(comboBox.component.selectedItem, property)
      }
    }

    var localProperty = items.random()
    panel {
      row {
        val comboBox = comboBox(items)
          .bindItem({ localProperty }, { localProperty = it!! })
        assertEquals(comboBox.component.selectedItem, localProperty)
      }
    }

    panel {
      row {
        val comboBox = comboBox(items)
          .bindItem(MutableProperty({ localProperty }, { localProperty = it!! }))
        assertEquals(comboBox.component.selectedItem, localProperty)
      }
    }
  }
}