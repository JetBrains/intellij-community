// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.annotations

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture
import org.jetbrains.plugins.groovy.lang.highlighting.grHighlightingFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The highlighting tests of a `TYPE_USE` annotation, for each Groovy version.
 */
class GrTypeUseAnnotationHighlightingTestBase private constructor() {
  companion object {
    private fun addExampleAnno(highlighting: GrHighlightingFixture) {
      highlighting.fixture.addClass("""
        import java.lang.annotation.ElementType
        import java.lang.annotation.Target
        @Target({ElementType.TYPE_USE})
        @interface ExampleAnno {}
      """.trimIndent())
    }
  }

  @TestApplication
  class Gr3TypeUseAnnotationHighlightingTest {
    companion object {
      private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0)
    }

    private val highlighting by grHighlightingFixture(projectFixture)

    @BeforeEach
    fun setUp() {
      addExampleAnno(highlighting)
    }

    @Test
    fun testAnnotationClass(): Unit = highlighting.doTestHighlighting("""
        @<error descr="'@ExampleAnno' annotation not allowed on annotation type">ExampleAnno</error>
        @interface InnerAnnotation {}
    """.trimIndent())

    @Test
    fun testNoHighlightingForTypeUse(): Unit = highlighting.doTestHighlighting("""
    @<error descr="'@ExampleAnno' annotation not allowed on type">ExampleAnno</error>
    class Main2 {
        @<error descr="'@ExampleAnno' annotation not allowed on field">ExampleAnno</error>
        private String field = null;
        
         @<error descr="'@ExampleAnno' annotation not allowed on constructor">ExampleAnno</error>
        Main2() {
        }
    
        static void main(String[] args) {
            method("string")
        }
    
        @<error descr="'@ExampleAnno' annotation not allowed on method">ExampleAnno</error>
        static String method(@<error descr="'@ExampleAnno' annotation not allowed on parameter">ExampleAnno</error> String s) {
            @<error descr="'@ExampleAnno' annotation not allowed on local variable">ExampleAnno</error> String t = "r";
        }
    }
  """.trimIndent())
  }

  @TestApplication
  class Gr4TypeUseAnnotationHighlightingTest {
    companion object {
      private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0)
    }

    private val highlighting by grHighlightingFixture(projectFixture)

    @BeforeEach
    fun setUp() {
      addExampleAnno(highlighting)
    }

    @Test
    fun testAnnotationClass(): Unit = highlighting.doTestHighlighting("""
        @ExampleAnno
        @interface InnerAnnotation {}
    """.trimIndent())

    @Test
    fun testNoHighlightingForTypeUse(): Unit = highlighting.doTestHighlighting("""
    @ExampleAnno
    class Main2 {
        @ExampleAnno
        private String field = null;
        
        @ExampleAnno
        Main2() {
        }

        static void main(String[] args) {
            method("string")
        }

        @ExampleAnno
        static String method(@ExampleAnno String s) {
            @ExampleAnno String t = "r";
        }
    }
  """.trimIndent())
  }
}