// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.highlighting

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.util.TestUtils


class GrAssignabilityGenericTest : GrHighlightingTestBase() {
  override fun getBasePath(): String = "${TestUtils.getTestDataPath()}/highlighting/assignabilityGeneric";

  override fun getCustomInspections(): Array<InspectionProfileEntry> = arrayOf(GroovyAssignabilityCheckInspection())

  override fun getProjectDescriptor(): LightProjectDescriptor = GroovyProjectDescriptors.GROOVY_2_5

  fun testGroovyNamedParamsInSource() = doTest()

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

    doTest()
  }
}