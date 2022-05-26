// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ButtonTest {

  var property = true

  private var boolean = true

  private var booleanGetSet: Boolean = false
    get() = field
    set(value) {
      field = value
    }

  private var booleanNoBackingField = true

  private var booleanNoBacking: Boolean
    get() = booleanNoBackingField
    set(value) {
      booleanNoBackingField = value
    }

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

  @Test
  fun testBindCheckBox() {
    testCheckBox(
      { checkBox("checkBox").bindSelected(::boolean) },
      { boolean })
    testCheckBox(
      { checkBox("checkBox").bindSelected({ boolean }, { boolean = it }) },
      { boolean })
    testCheckBox(
      { checkBox("checkBox").bindSelected(PropertyBinding({ boolean }, { boolean = it })) },
      { boolean })

    // Test different kinds of properties
    testCheckBox(
      { checkBox("checkBox").bindSelected(::booleanGetSet) },
      { booleanGetSet })
    testCheckBox(
      { checkBox("checkBox").bindSelected(::booleanNoBacking) },
      { booleanNoBacking })
    val data = Data(true)
    testCheckBox(
      { checkBox("checkBox").bindSelected(data::boolean) },
      { data.boolean })
    val javaData = JavaBooleanData()
    testCheckBox(
      { checkBox("checkBox").bindSelected(javaData::bool1) },
      { javaData.bool1 })
    testCheckBox(
      { checkBox("checkBox").bindSelected(javaData::bool2) },
      { javaData.bool2 })
  }

  private fun testCheckBox(createCell: Row.() -> Cell<JBCheckBox>, getter: () -> Boolean) {
    lateinit var component: JBCheckBox
    val panel = panel {
      row {
        component = createCell().component
      }
    }

    val initialValue = component.isSelected
    panel.reset()
    assertEquals(initialValue, getter())

    component.isSelected = !component.isSelected
    assertNotEquals(component.isSelected, getter())
    assertNotEquals(component.isSelected, initialValue)

    panel.apply()
    assertEquals(component.isSelected, getter())
    assertNotEquals(component.isSelected, initialValue)
  }

  private data class Data(var boolean: Boolean)

}
