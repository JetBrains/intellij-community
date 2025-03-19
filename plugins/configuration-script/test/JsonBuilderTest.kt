package com.intellij.configurationScript

import com.intellij.testFramework.assertions.Assertions.assertThat
import org.jetbrains.io.json
import org.junit.Test

class JsonBuilderTest {
  @Test
  fun jsonBuilder() {
    val stringBuilder = StringBuilder()
    stringBuilder.json {
      "foo" to "bar"
    }
    assertThat(stringBuilder.toString()).isEqualTo("""
     {
       "foo": "bar"
     }
     """.trimIndent())
  }

  @Test
  fun `several and boolean`() {
    val stringBuilder = StringBuilder()
    stringBuilder.json {
      "foo" to "bar"
      "p2" to false
      "p3" to true
      "p4" to false
    }
    assertThat(stringBuilder.toString()).isEqualTo("""
     {
       "foo": "bar","p2": false,"p3": true,"p4": false
     }
     """.trimIndent())
  }

  @Test
  fun `several and int`() {
    val stringBuilder = StringBuilder()
    stringBuilder.json {
      "foo" to "bar"
      "p2" to 42
      "p3" to 24
    }
    assertThat(stringBuilder.toString()).isEqualTo("""
     {
       "foo": "bar","p2": 42,"p3": 24
     }
     """.trimIndent())
  }

  @Test
  fun `child object`() {
    val stringBuilder = StringBuilder()
    stringBuilder.json {
      "foo" to "bar"
      map("p2") {
        "a" to "b"
        "c" to true
      }
    }
    assertThat(stringBuilder.toString()).isEqualTo("""
     {
       "foo": "bar",
       "p2": {
         "a": "b","c": true
       }
     }
     """.trimIndent())
  }

  @Test
  fun `2 level child object`() {
    val stringBuilder = StringBuilder()
    stringBuilder.json {
      "foo" to "bar"
      map("one") {
        map("two") {
          "twoP1" to true
        }
      }
    }
    assertThat(stringBuilder.toString()).isEqualTo("""
     {
       "foo": "bar",
       "one": {
         "two": {
           "twoP1": true
         }
       }
     }
     """.trimIndent())
  }
}

