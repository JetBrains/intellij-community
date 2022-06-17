// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.frameworkSupport.script.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScriptBuilderTest {

  @Test
  fun `test script properties`() {
    val tree = tree {
      property("value", "10")
      property("value", 10)
      property("value", false)
      property("value", call("foo") {
        call("create", "string")
      })
    }
    test(GROOVY, tree, """
      |def value = '10'
      |def value = 10
      |def value = false
      |def value = foo {
      |    create 'string'
      |}
    """.trimMargin())
    test(KOTLIN, tree, """
      |var value = "10"
      |var value = 10
      |var value = false
      |var value = foo {
      |    create("string")
      |}
    """.trimMargin())
  }

  private fun test(builder: ScriptBuilder, tree: ScriptElement.Statement.Expression.BlockElement, expected: String) {
    assertEquals(expected, builder.generate(tree))
  }

  companion object {
    private val GROOVY = GroovyScriptBuilder()
    private val KOTLIN = KotlinScriptBuilder()
  }
}