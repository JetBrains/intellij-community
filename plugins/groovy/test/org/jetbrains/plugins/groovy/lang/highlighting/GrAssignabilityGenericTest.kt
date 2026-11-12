// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LibraryLightProjectDescriptor
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GrAssignabilityGenericTest {
  private val highlighting by grHighlightingFixture(projectFixture, "${TestUtils.getAbsoluteTestDataPath()}/highlighting/assignabilityGeneric", customInspections = { arrayOf(GroovyAssignabilityCheckInspection()) })
  private val myFixture get() = highlighting.fixture

  @BeforeEach
  fun setUp() {

    myFixture.addClass("""
      package org.example;
      
      public class BaseClass {}
    """.trimIndent())

    myFixture.addClass("""
      package org.example;
      
      public class DerivedClass extends BaseClass {}
    """.trimIndent())

    myFixture.addClass("""
      package org.example;
      
      public class SideClass {}
    """.trimIndent())
  }

  @Test
  fun testGroovyNamedParamsInSource(): Unit = highlighting.doTest()

  @Test
  fun testJavaNamedParamsInSource() {
    @Language("JAVA") val classText = """
      package org.example;    
        
      import groovy.transform.NamedParam;
      import groovy.transform.NamedParams;
      import java.util.Map;

      public class Util {       
        public static <T> T methodWithRawType(@NamedParams({@NamedParam(value = 'name', type = String.class)}) Map obj, T value) {
          return value;
        }
      
        public static <T> T methodWithBoundType(@NamedParams({@NamedParam(value = 'name', type = String.class)}) Map<String, String> obj, T value) {
          return value;
        }
      
        public static <T> T methodWithObjectType(@NamedParams({@NamedParam(value = 'name', type = String.class)}) Map<String, Object> obj, T value) {
          return value;
        }
      }
    """.trimIndent()

    myFixture.addClass(classText)

    highlighting.doTest()
  }

  @Test
  fun testSpockMethods(): Unit = highlighting.doTest()

  companion object {
    private val PROJECT_DESCRIPTOR = LibraryLightProjectDescriptor(
      GroovyProjectDescriptors.LIB_GROOVY_2_5 + RepositoryTestLibrary("org.spockframework:spock-core:2.0-groovy-2.5")
    )
    private val projectFixture = lightJavaProjectFixture(PROJECT_DESCRIPTOR)
  }
}