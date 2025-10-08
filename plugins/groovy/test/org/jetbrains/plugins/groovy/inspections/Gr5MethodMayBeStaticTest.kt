// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class Gr5MethodMayBeStaticTest : GrMethodMayBeStaticTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

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