// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.jupiter.api.fail
import javax.swing.border.Border
import javax.swing.border.EmptyBorder

class TransferablePropertiesTest {

  enum class MockEnum {
    EnumItem1,
    EnumItem2,
  }

  @Test
  fun testSerialization() {
    @Suppress("unused")
    val state = object : ObservableState() {
      var intProp1 by property(0)
      var intProp2 by property(-42)
      var intProp3 by property(+42)
      var intProp4 by property(Integer.MIN_VALUE)
      var intProp5 by property(Integer.MAX_VALUE)
      var intProp6: Int by property(42)
      var intProp7: Int? by property(42)
      var intProp8: Int? by property(null)
      var intProp9 by property(null as Int?)
      var floatProp1 by property(0f)
      var floatProp2 by property(-4.2f)
      var floatProp3 by property(+4.2f)
      var floatProp4 by property(Float.MIN_VALUE)
      var floatProp5 by property(Float.MAX_VALUE)
      var floatProp6 by property(Float.NEGATIVE_INFINITY)
      var floatProp7 by property(Float.POSITIVE_INFINITY)
      var floatProp8 by property(Float.NaN)
      var strProp1 by property(null as String?)
      var strProp2 by property("")
      var strProp3 by property("some text")
      var boolProp1 by property(false)
      var boolProp2 by property(true)
      var enumItemProp1 by property(MockEnum.EnumItem1)
      var enumItemProp2 by property(MockEnum.EnumItem2)
      var nullableEnumItemProp1 by property(null as MockEnum?)
      var nullableEnumItemProp2: MockEnum? by property(null)
      var listInt1 by property(listOf(1, 2, 3))
      var listIntNullable1 by property(listOf(1, 2, 3, null as Int?))
      var listIntNullable2: List<Int?> by property(listOf(1, 2, 3, null))
      var mutableListInt1 by property(mutableListOf(1, 2, 3))
      var mutableListIntNullable1 by property(mutableListOf(1, 2, 3, null as Int?))
      var mutableListIntNullable2: List<Int?> by property(mutableListOf(1, 2, 3, null))
    }.init()

    for (property in state.__getProperties()) {
      try {
        checkEncodingAndDecodingEquality(state, property)
      } catch (e: Exception) {
        fail("property.name=${property.name}, property.value=${property.getValue(state)}", e)
      }
    }
  }

  @Test
  fun testNonSerializable() {
    @Suppress("unused")
    val state = object : ObservableState() {
      var prop1: Any by property(42)
      var list2: List<Any> by property(listOf(1, 2, 3))
      var list4: MutableList<Any> by property(mutableListOf(1, 2, 3))
      var chars: CharSequence by property("text")  // CharSequence interface shouldn't be serializable
      var border: Border by property(EmptyBorder(1,2,3,4))
    }

    for (property in state.__getProperties()) {
      assertFalse("property.name=${property.name}, property.value=${property.getValue(state)}", property is TransferableProperty<*>)
    }
  }

  private fun checkEncodingAndDecodingEquality(state: ObservableState, property: StateProperty<*>) {
    property as TransferableProperty<*>
    val encoded = property.encodeToString()
    val decoded = property.decodeFromString(encoded)
    assertEquals(property.getValue(state), decoded)
  }

  @Test
  fun testModifyingProperties() {
    val state = object : ObservableState() {
      var prop1: String? by property("initial", SyncDefaultValueCalculator { "default calculated" })
    }.init()

    assertEquals("default calculated", state.prop1)

    state.prop1 = "overridden1"
    assertEquals("overridden1", state.prop1)

    state.prop1 = null
    assertEquals(null, state.prop1)

    state.prop1 = "overridden2"
    assertEquals("overridden2", state.prop1)

    state.clearOverriding(state::prop1)
    assertEquals("default calculated", state.prop1)
  }
}