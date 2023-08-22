// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.junit.jupiter.api.Test

class ScriptBuilderTest : ScriptBuilderTestCase() {

  @Test
  fun `test script properties`() {
    assertScript("""
      |def value = '10'
      |def value = 10
      |def value = false
      |def value = foo {
      |    create 'string'
      |}
    """.trimMargin(), """
      |var value = "10"
      |var value = 10
      |var value = false
      |var value = foo {
      |    create("string")
      |}
    """.trimMargin()) {
      property("value", "10")
      property("value", 10)
      property("value", false)
      property("value", call("foo") {
        call("create", "string")
      })
    }
  }
}