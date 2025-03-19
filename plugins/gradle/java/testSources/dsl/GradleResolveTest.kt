// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.CommonClassNames.JAVA_UTIL_DATE
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest

class GradleResolveTest: GradleCodeInsightTestCase() {

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test resolve date constructor`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      testBuildscript("<caret>new Date()") {
        val expression = elementUnderCaret(GrNewExpression::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertTrue(method.isConstructor)
        assertEquals(JAVA_UTIL_DATE, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun `test resolve date constructor 2`(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      testBuildscript("<caret>new Date(1l)") {
        val expression = elementUnderCaret(GrNewExpression::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertTrue(method.isConstructor)
        assertEquals(JAVA_UTIL_DATE, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testClosureParameterIsTheSameAsDelegate(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("""
        tasks.register('jc', Jar) {
            it.man<caret>ifest {}
        }
      """.trimIndent()) {
        val expression = elementUnderCaret(GrMethodCall::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        assertInstanceOf<PsiMethod>(results[0].element)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGradleGeneratedSetters(gradleVersion: GradleVersion) {
    test(gradleVersion, BUILD_SRC_FIXTURE) {
      testBuildscript("""
        tasks.register("myTask", MyTask) {
          myFirstPro<caret>perty = "value" // ok
      }
      """.trimIndent()) {
        val expression = elementUnderCaret(GrReferenceExpression::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setMyFirstProperty", method.name)
        assertEquals("getMyFirstProperty", (method.navigationElement as PsiMethod).name)
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource
  fun testGradleGeneratedSetters2(gradleVersion: GradleVersion) {
    test(gradleVersion, BUILD_SRC_FIXTURE) {
      testBuildscript("""
        tasks.register("myTask", MyTask) {
          myCollec<caret>tion = files("hello")
      }
      """.trimIndent()) {
        val expression = elementUnderCaret(GrReferenceExpression::class.java)
        val results = expression.multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setMyCollection", method.name)
        assertEquals("getMyCollection", (method.navigationElement as PsiMethod).name)
      }
    }
  }

  companion object {
    private val BUILD_SRC_FIXTURE = GradleTestFixtureBuilder.create("GradleResolveTest-buildSrc") { gradleVersion ->
      withSettingsFile(gradleVersion) {
        setProjectName("GradleResolveTest-buildSrc")
      }
      withFile("buildSrc/src/main/java/MyTask.java", """
        import org.gradle.api.Action;
        import org.gradle.api.DefaultTask;
        import org.gradle.api.file.ConfigurableFileCollection;
        import org.gradle.api.provider.Property;
        import org.gradle.api.tasks.Input;
        import org.gradle.api.tasks.InputFiles;
        
        public abstract class MyTask extends DefaultTask {
        
            @Input
            public abstract Property<String> getMyFirstProperty();
            
            @InputFiles
            public abstract ConfigurableFileCollection getMyCollection();
        
        }
      """.trimIndent())
    }
  }
}