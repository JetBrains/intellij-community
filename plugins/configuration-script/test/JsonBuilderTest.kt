package com.intellij.configurationScript

import com.intellij.testFramework.assertions.Assertions
import org.junit.Test

class JsonBuilderTest {
  @Test
   fun jsonBuilder() {
     val stringBuilder = StringBuilder()
    stringBuilder.json {
       "foo" to "bar"
     }
     Assertions.assertThat(stringBuilder.toString()).isEqualTo("""
     {"foo":"bar"}
     """.trimIndent())
   }

   @Test
   fun `several and boolean`() {
     val stringBuilder = StringBuilder()
     stringBuilder.json {
       "foo" to "bar"
       "p2" to false
     }
     Assertions.assertThat(stringBuilder.toString()).isEqualTo("""
     {"foo":"bar","p2":false}
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
     Assertions.assertThat(stringBuilder.toString()).isEqualTo("""
     {"foo":"bar","p2":{
     "a":"b","c":true
     }}
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
     Assertions.assertThat(stringBuilder.toString()).isEqualTo("""
     {"foo":"bar","one":{
     "two":{
     "twoP1":true
     }
     }}
     """.trimIndent())
   }
}

