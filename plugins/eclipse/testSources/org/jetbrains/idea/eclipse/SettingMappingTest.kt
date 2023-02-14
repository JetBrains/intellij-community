// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SettingMappingTest {
  class TestObject {
    var boolField = false
    var intField = 0
  }

  var obj = TestObject()

  @BeforeEach
  fun resetTestObject() {
    obj = TestObject()
  }

  @Test
  fun testFieldSettingMapping() {
    val mapping = field(obj::boolField).convert(BooleanConvertor)
    assertEquals("false", mapping.export())
    mapping.import("True")
    assertEquals(true, obj.boolField)
  }

  @Test
  fun testUnexpectedIncomingValue() {
    val mapping = field(obj::intField).convert(IntConvertor)
    assertThrows<UnexpectedIncomingValue> { mapping.import("not a number") }
    assertEquals(0, obj.intField)
  }

  @Test
  fun testDoNotImportSettingMapping() {
    val mapping = field(obj::boolField).doNotImport()
    assertTrue(mapping.isExportAllowed)
    assertEquals(false, mapping.export())
    assertFalse(mapping.isImportAllowed)
    assertThrows<IllegalStateException> { mapping.import(true) }
  }

  @Test
  fun testConstSettingMapping() {
    val mapping = const(true).convert(BooleanConvertor)
    assertTrue(mapping.isExportAllowed)
    assertEquals("true", mapping.export())
    assertFalse(mapping.isImportAllowed)
    assertThrows<IllegalStateException> { mapping.import("true") }
  }
  
  @Test
  fun testManualSettingMapping() {
    val mapping = compute(
      import = { obj.boolField = it },
      export = { obj.boolField }
    )

    assertTrue(mapping.isExportAllowed)
    assertTrue(mapping.isImportAllowed)
    assertEquals(false, obj.boolField)
    mapping.import(true)
    assertEquals(true, obj.boolField)
  }

  @Test
  fun testInvertingBooleanSettingMapping() {
    val mapping = field(obj::boolField).invert()
    assertEquals(true, mapping.export())
    mapping.import(false)
    assertEquals(true, obj.boolField)
  }
}