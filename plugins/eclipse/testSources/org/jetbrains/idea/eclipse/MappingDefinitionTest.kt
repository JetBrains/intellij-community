// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import org.jetbrains.idea.eclipse.codeStyleMapping.util.*
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.const
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.field
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.ignored
import org.jetbrains.idea.eclipse.codeStyleMapping.util.SettingsMappingHelpers.compute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MappingDefinitionTest {
  class TestObject {
    var boolField = false
    var intField = 0
    var stringField = "(string)"
  }

  var obj = TestObject()

  @BeforeEach
  fun resetTestObject() {
    obj = TestObject()
  }

  @Test
  fun testMappingDefinition() {
    val mappingDefinition = MappingDefinitionBuilder().apply {
      "option1" mapTo field(obj::boolField).invert().convert(BooleanConvertor)
      "option2" mapTo field(obj::intField).convert(IntConvertor).doNotImport()
      "option3" mapTo compute(
        import = { value -> obj.stringField = "($value)" },
        export = { obj.stringField.substring(1, obj.stringField.length - 1) }
      )
      "option4" mapTo const("constant")
      "option5" mapTo ignored()
    }.build()

    val expectedExport = mapOf(
      "option1" to "true",
      "option2" to "0",
      "option3" to "string",
      "option4" to "constant",
    )
    assertEquals(expectedExport, mappingDefinition.exportSettings().toMap())

    val toImport = mapOf(
      "option1" to "FALSE",
      "option2" to "1",
      "option3" to "another string",
      "option4" to "variable",
      "option5" to "ignored"
    )
    mappingDefinition.importSettings(toImport)

    assertEquals(true, obj.boolField)
    assertEquals(0, obj.intField)
    assertEquals("(another string)", obj.stringField)
  }

  @Test
  fun testImportsArePerformedInDefinedOrder() {
    val mappingDefinition1 = MappingDefinitionBuilder().apply {
      "option1" mapTo field(obj::boolField).convert(BooleanConvertor)
      "option2" mapTo compute(
        import = { obj.intField = if (obj.boolField) it * 10 else it * 5 },
        export = { obj.intField }
      ).convert(IntConvertor)
    }.build()

    val toImport = mapOf(
      "option2" to "10",
      "option1" to "true"
    )

    mappingDefinition1.importSettings(toImport)
    assertEquals(100, obj.intField)

    resetTestObject()

    val mappingDefinition2 = MappingDefinitionBuilder().apply {
      "option2" mapTo compute(
        import = { obj.intField = if (obj.boolField) it * 10 else it * 5 },
        export = { obj.intField }
      ).convert(IntConvertor)
      "option1" mapTo field(obj::boolField).convert(BooleanConvertor)
    }.build()

    mappingDefinition2.importSettings(toImport)
    assertEquals(50, obj.intField)
  }

  @Test
  fun testInvalidOptions() {
    val mappingDefinition = MappingDefinitionBuilder().apply {
      "option1" mapTo field(obj::boolField).convert(BooleanConvertor)
      "option2" mapTo field(obj::intField).convert(IntConvertor)
    }.build()

    val toImport = mapOf(
      "option1" to "invalid",
      "option2" to "10"
    )

    val problems = mappingDefinition.importSettings(toImport)
    assertEquals(listOf("option1=invalid"), problems)
    assertEquals(false, obj.boolField, "Field is not changed in case of an error")
    assertEquals(10, obj.intField, "Import of a field is not affected by an error elsewhere")
  }
}