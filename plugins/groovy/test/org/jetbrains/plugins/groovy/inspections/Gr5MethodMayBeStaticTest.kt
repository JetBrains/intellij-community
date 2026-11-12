// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.junit.jupiter.api.Test

@TestApplication
class Gr5MethodMayBeStaticTest : GrMethodMayBeStaticTestBase(projectFixture) {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  @Test
  fun testNewMain() {
    doTest("""
             class A {
                 void main() {
                   println "Hello world!"
                 }
             }
             """.trimIndent())
  }
}