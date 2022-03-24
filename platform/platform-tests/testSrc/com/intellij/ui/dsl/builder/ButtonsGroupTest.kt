// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.dsl.UiDslException
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.KMutableProperty0

class ButtonsGroupTest {

  enum class Enum1 {
    VALUE1,
    VALUE2
  }

  /**
   * Enum with overriding, so Enum2 class and Enum2.VALUE1 class are different
   */
  enum class Enum2(val id: Int) {
    VALUE1(1) {
      override fun toString(): String {
        return "$id"
      }
    },
    VALUE2(2) {
      override fun toString(): String {
        return "$id"
      }
    }
  }

  var boolean = true
  var int = 1
  var string = "a"
  var enum1 = Enum1.VALUE1
  var enum2 = Enum2.VALUE1

  @Test
  fun testInvalidGroups() {
    assertThrows<UiDslException> {
      testButtonsGroup(1, 2)
    }
    assertThrows<UiDslException> {
      testButtonsGroup("1", "2", ::int)
    }
    assertThrows<UiDslException> {
      testButtonsGroup(null, null, ::int)
    }
  }

  @Test
  fun testValidGroups() {
    testButtonsGroup(null, null)
    testButtonsGroup(true, false, ::boolean)
    testButtonsGroup(1, 2, ::int)
    testButtonsGroup("b", "c", ::string)
    testButtonsGroup(Enum1.VALUE1, Enum1.VALUE2, ::enum1)
    testButtonsGroup(Enum2.VALUE1, Enum2.VALUE2, ::enum2)
  }

  private fun testButtonsGroup(value1: Any?, value2: Any?) {
    panel {
      buttonsGroup(value1, value2)
    }
  }

  private inline fun <reified T : Any> testButtonsGroup(value1: Any?, value2: Any?, prop: KMutableProperty0<T>) {
    panel {
      buttonsGroup(value1, value2)
        .bind(prop)
    }
  }

  private fun Panel.buttonsGroup(value1: Any?, value2: Any?): ButtonsGroup {
    return buttonsGroup {
      row {
        radioButton("radioButton1", value1)
      }
      row {
        radioButton("radioButton2", value2)
      }
    }
  }
}
