// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.schema.parser

import com.google.gson.Gson
import org.editorconfig.language.schema.descriptors.impl.*
import org.editorconfig.mock.EditorConfigMockLogger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test


class EditorConfigOptionDescriptorJsonDeserializerTest {
  private val arrayClass = Array<EditorConfigOptionDescriptor?>::class.java

  private lateinit var gson: Gson
  private lateinit var logger: EditorConfigMockLogger

  @Before
  fun initialize() {
    logger = EditorConfigMockLogger()
    gson = EditorConfigOptionDescriptorJsonDeserializer.buildGson(logger)
  }

  @Test
  fun `test single object deserialization`() {
    val text =
      """{
        |  "type": "option",
        |  "key": "tab_width",
        |  "value": {"type": "number"}
        |}""".trimMargin()

    val parsed = gson.fromJson(text, EditorConfigOptionDescriptor::class.java)
    val expected = EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("tab_width", null, null),
                                                EditorConfigNumberDescriptor(null, null), null, null)

    assertEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test array deserialization`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "tab_width",
        |    "value": {
        |      "type": "number"
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOf(
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("tab_width", null, null), EditorConfigNumberDescriptor(null, null), null,
                                   null))

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test dummy string deserialization`() {
    val text = "[]"

    val descriptors = gson.fromJson(text, arrayClass)
    val expected = arrayOf<EditorConfigOptionDescriptor>()

    assertArrayEquals(expected, descriptors)
    logger.assertCallNumbers()
  }

  @Test
  fun `test that gson deserializes typeless descriptor into null`() {
    val text =
      """[
        |  {
        |    "key": "tab_width",
        |    "value": {"type": "number"}
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOfNulls<EditorConfigOptionDescriptor?>(1)

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers(warnCalls = 1)
    logger.assertLastMessage("""Found illegal descriptor: {"key":"tab_width","value":{"type":"number"}}""")
  }

  @Test
  fun `test that gson deserializes incomplete descriptor into null`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "tab_width"
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOfNulls<EditorConfigOptionDescriptor?>(1)

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers(warnCalls = 1)
    logger.assertLastMessage("""Found illegal descriptor: {"type":"option","key":"tab_width"}""")
  }

  @Test
  fun `test that gson issues a warning when descriptor contains unknown key`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "test",
        |    "value": {
        |      "type": "number",
        |      "documentation": "this is test data",
        |      "unknown_key": "value"
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOf(
      EditorConfigOptionDescriptor(
        EditorConfigConstantDescriptor("test", null, null),
        EditorConfigNumberDescriptor("this is test data", null),
        null, null
      )
    )

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers(warnCalls = 1)
    logger.assertLastMessage("Unexpected option value descriptor key in [type, documentation, unknown_key]")
  }

  @Test
  fun `test that gson correctly deserializes complex descriptor`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "csharp_new_line_before_open_brace",
        |    "value": {
        |      "type": "pair",
        |      "first": {
        |        "type": "union",
        |          "values": [
        |            "all",
        |            "none",
        |            {
        |              "type": "list",
        |              "values": [
        |              "accessors",
        |              "anonymous_methods",
        |              "anonymous_types",
        |              "control_blocks",
        |              "events",
        |              "indexers",
        |              "lambdas",
        |              "local_functions",
        |              "methods",
        |              "object_collection",
        |              "properties",
        |              "types"
        |            ]
        |          }
        |        ]
        |      },
        |      "second": {
        |        "type": "union",
        |        "values": [
        |          "none",
        |          "silent",
        |          "suggestion",
        |          "warning",
        |          "error"
        |        ]
        |      }
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOf(
      EditorConfigOptionDescriptor(
        EditorConfigConstantDescriptor("csharp_new_line_before_open_brace", null, null),
        EditorConfigPairDescriptor(
          EditorConfigUnionDescriptor(
            listOf(
              EditorConfigConstantDescriptor("all", null, null),
              EditorConfigConstantDescriptor("none", null, null),
              EditorConfigListDescriptor(
                0,
                false,
                listOf(
                  "accessors",
                  "anonymous_methods",
                  "anonymous_types",
                  "control_blocks",
                  "events",
                  "indexers",
                  "lambdas",
                  "local_functions",
                  "methods",
                  "object_collection",
                  "properties",
                  "types"
                ).map { EditorConfigConstantDescriptor(it, null, null) }, null, null)
            ), null, null
          ),
          EditorConfigUnionDescriptor(
            listOf(
              EditorConfigConstantDescriptor("none", null, null),
              EditorConfigConstantDescriptor("silent", null, null),
              EditorConfigConstantDescriptor("suggestion", null, null),
              EditorConfigConstantDescriptor("warning", null, null),
              EditorConfigConstantDescriptor("error", null, null)
            ), null, null), null, null
        ), null, null
      ))

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test that parser allows lists with unions`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "required_modifiers",
        |    "value": {
        |      "type": "list",
        |      "values": [
        |        {
        |          "type": "union",
        |          "values": [
        |            "abstract",
        |            "must_inherit"
        |          ]
        |        },
        |        "async",
        |        "const",
        |        "readonly",
        |        {
        |          "type": "union",
        |          "values": [
        |            "static",
        |            "shared"
        |          ]
        |        }
        |      ]
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOf(
      EditorConfigOptionDescriptor(
        EditorConfigConstantDescriptor("required_modifiers", null, null),
        EditorConfigListDescriptor(
          0,
          false,
          listOf(
            EditorConfigUnionDescriptor(
              listOf(
                EditorConfigConstantDescriptor("abstract", null, null),
                EditorConfigConstantDescriptor("must_inherit", null, null)
              ), null, null
            ),
            EditorConfigConstantDescriptor("async", null, null),
            EditorConfigConstantDescriptor("const", null, null),
            EditorConfigConstantDescriptor("readonly", null, null),
            EditorConfigUnionDescriptor(
              listOf(
                EditorConfigConstantDescriptor("static", null, null),
                EditorConfigConstantDescriptor("shared", null, null)
              ), null, null
            )
          ), null, null
        ), null, null
      )
    )

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test parsing options with complex keys`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": {
        |      "type": "qualified",
        |      "values": [
        |        "dotnet_naming_symbols",
        |        {
        |          "type": "declaration",
        |          "id": "naming_symbols_title",
        |          "needs_references": true
        |        },
        |        "applicable_accessibilities"
        |      ]
        |    },
        |    "value": {
        |      "type": "union",
        |      "values": [
        |        "*",
        |        {
        |          "type": "list",
        |          "values": [
        |            "public",
        |            {
        |              "type": "union",
        |              "values": [
        |                "internal",
        |                "friend"
        |              ]
        |            },
        |            "private",
        |            "protected",
        |            {
        |              "type": "union",
        |              "values": [
        |                "protected_internal",
        |                "protected_friend"
        |              ]
        |            }
        |          ]
        |        }
        |      ]
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)
    val expected = arrayOf(
      EditorConfigOptionDescriptor(
        EditorConfigQualifiedKeyDescriptor(
          listOf(
            EditorConfigConstantDescriptor("dotnet_naming_symbols", null, null),
            EditorConfigDeclarationDescriptor("naming_symbols_title", true, false, null, null),
            EditorConfigConstantDescriptor("applicable_accessibilities", null, null)
          ), null, null
        ),
        EditorConfigUnionDescriptor(
          listOf(
            EditorConfigConstantDescriptor("*", null, null),
            EditorConfigListDescriptor(
              0,
              false,
              listOf(
                EditorConfigConstantDescriptor("public", null, null),
                EditorConfigUnionDescriptor(
                  listOf(
                    EditorConfigConstantDescriptor("internal", null, null),
                    EditorConfigConstantDescriptor("friend", null, null)
                  ), null, null
                ),
                EditorConfigConstantDescriptor("private", null, null),
                EditorConfigConstantDescriptor("protected", null, null),
                EditorConfigUnionDescriptor(
                  listOf(
                    EditorConfigConstantDescriptor("protected_internal", null, null),
                    EditorConfigConstantDescriptor("protected_friend", null, null)
                  ), null, null
                )
              ), null, null
            )
          ), null, null
        ), null, null
      )
    )

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test that type aliases are parsed correctly`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "foo",
        |    "value": {
        |      "type": "union",
        |      "values": [
        |        "true",
        |        "false"
        |      ],
        |      "type_alias": "boolean"
        |    }
        |  },
        |  {
        |    "type": "option",
        |    "key": "bar",
        |    "value": {
        |      "type": "boolean"
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)

    val boolean = EditorConfigUnionDescriptor(
      listOf(
        EditorConfigConstantDescriptor("true", null, null),
        EditorConfigConstantDescriptor("false", null, null)
      ), null, null
    )

    val expected = arrayOf(
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("foo", null, null), boolean, null, null),
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("bar", null, null), boolean, null, null)
    )

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test that attempting to define same type alias twice with same values is allowed`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "foo",
        |    "value": {
        |      "type": "union",
        |      "type_alias": "boolean",
        |      "values": [
        |        "true",
        |        "false"
        |      ]
        |    }
        |  },
        |  {
        |    "type": "option",
        |    "key": "bar",
        |    "value": {
        |      "type": "union",
        |      "type_alias": "boolean",
        |      "values": ["true", "false"]
        |    }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)

    val boolean = EditorConfigUnionDescriptor(
      listOf(
        EditorConfigConstantDescriptor("true", null, null),
        EditorConfigConstantDescriptor("false", null, null)
      ), null, null
    )

    val expected = arrayOf(
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("foo", null, null), boolean, null, null),
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("bar", null, null), boolean, null, null)
    )

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers()
  }

  @Test
  fun `test that attempting to define same type alias with different values is prohibited`() {
    val text =
      """[
        |  {
        |    "type": "option",
        |    "key": "foo",
        |    "value": {
        |      "type": "union",
        |      "type_alias": "boolean",
        |      "values": [
        |        "true",
        |        "false",
        |        "unknown"
        |      ]
        |    }
        |  },
        |  {
        |    "type": "option",
        |    "key": "bar",
        |    "value": {
        |      "type": "union",
        |      "type_alias": "boolean",
        |      "values": [
        |        "true",
        |        "false"
        |      ]
        |    }
        |  },
        |  {
        |    "type": "option",
        |    "key": "bas",
        |    "value": { "type": "boolean" }
        |  }
        |]""".trimMargin()

    val parsed = gson.fromJson(text, arrayClass)

    val strangeBoolean = EditorConfigUnionDescriptor(
      listOf(
        EditorConfigConstantDescriptor("true", null, null),
        EditorConfigConstantDescriptor("false", null, null),
        EditorConfigConstantDescriptor("unknown", null, null)
      ), null, null
    )

    val boolean = EditorConfigUnionDescriptor(
      listOf(
        EditorConfigConstantDescriptor("true", null, null),
        EditorConfigConstantDescriptor("false", null, null)
      ), null, null
    )

    val expected = arrayOf(
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("foo", null, null), strangeBoolean, null, null),
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("bar", null, null), boolean, null, null),
      EditorConfigOptionDescriptor(EditorConfigConstantDescriptor("bas", null, null), strangeBoolean, null, null)
    )

    assertArrayEquals(expected, parsed)
    logger.assertCallNumbers(warnCalls = 1)
  }
}
