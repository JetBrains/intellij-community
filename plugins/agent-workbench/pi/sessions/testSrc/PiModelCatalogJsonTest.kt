// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.core.json.JsonFactory

class PiModelCatalogJsonTest {
  @Test
  fun parsesNestedJsonObjectValues() {
    val root = checkNotNull(
      JsonFactory().parseJsonObject(
        """
          {
            "name": "qwen",
            "count": 3,
            "float": 1.5,
            "enabled": true,
            "missing": null,
            "nested": {"value": "x"},
            "list": ["a", 2, false, null, {"id": "m"}],
            "stringValues": ["a", 2, false, null, {"id": "m"}]
          }
        """.trimIndent()
      )
    )

    assertThat(root.stringValue("name")).isEqualTo("qwen")
    assertThat(root.intValue("count")).isEqualTo(3)
    assertThat(root.intValue("float")).isEqualTo(1)
    assertThat(root.booleanValue("enabled")).isTrue()
    assertThat(root.objectValue("nested")?.stringValue("value")).isEqualTo("x")
    assertThat(root.listValue("list")).containsExactly("a", 2L, false, null, mapOf("id" to "m"))
    assertThat(root.stringListValue("stringValues")).containsExactly("a", "2", "false")
  }

  @Test
  fun returnsNullForNonObjectJson() {
    val jsonFactory = JsonFactory()

    assertThat(jsonFactory.parseJsonObject("[]")).isNull()
    assertThat(jsonFactory.parseJsonObject("null")).isNull()
  }

  @Test
  fun parsesBooleanStrictlyUnlessOptionsAreEnabled() {
    val values = mapOf(
      "yes" to "yes",
      "true" to "true",
      "false" to "false",
      "trimmed" to " true ",
    )

    assertThat(values.booleanValue("yes")).isNull()
    assertThat(values.booleanValue("yes", allowYes = true)).isTrue()
    assertThat(values.booleanValue("true")).isTrue()
    assertThat(values.booleanValue("false")).isFalse()
    assertThat(values.booleanValue("trimmed")).isNull()
    assertThat(values.booleanValue("trimmed", trimString = true)).isTrue()
  }

  @Test
  fun padsBase64Url() {
    assertThat("abc".padBase64Url()).isEqualTo("abc=")
    assertThat("ab".padBase64Url()).isEqualTo("ab==")
    assertThat("abcd".padBase64Url()).isEqualTo("abcd")
  }
}
