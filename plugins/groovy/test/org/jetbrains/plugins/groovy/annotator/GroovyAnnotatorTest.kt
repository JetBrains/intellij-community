// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import org.jetbrains.plugins.groovy.LightGroovyTestCase

class GroovyAnnotatorTest: LightGroovyTestCase() {
  fun testMultiAssignment() {
    myFixture.configureByText("a.groovy", """
      void f() {
        def x = 0
        def y = 1
        (x, y) = [-1, 0]
        var (Integer a, b) = [1, 2]
        def (Integer c, d) = [3, 4]
        
        <error descr="Tuple declaration should end with 'def' or 'var' modifier">final</error> (Integer e, f) = [5, 6]
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

}