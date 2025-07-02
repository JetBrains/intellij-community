// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.assertInstanceOf
import groovy.lang.Closure.DELEGATE_FIRST
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest

class GradleProjectTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve explicit getter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>getGroup()") {
        val results = elementUnderCaret(GrMethodCall::class.java).multiResolveGroovy(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("getGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve property`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group") {
        val results = elementUnderCaret(GrReferenceExpression::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("getGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve explicit setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>setGroup(1)") {
        val results = elementUnderCaret(GrMethodCall::class.java).multiResolveGroovy(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve explicit setter without argument`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>setGroup()") {
        val results = elementUnderCaret(GrMethodCall::class.java).multiResolveGroovy(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve property setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group = 42") {
        val results = elementUnderCaret(GrReferenceExpression::class.java).multiResolve(false)
        assertEquals(1, results.size)
        val method = assertInstanceOf<PsiMethod>(results[0].element)
        assertEquals("setGroup", method.name)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve implicit setter`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group(42)") {
        setterMethodTest("group", "setGroup", GRADLE_API_PROJECT)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test resolve implicit setter without argument`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>group()") {
        setterMethodTest("group", "setGroup", GRADLE_API_PROJECT)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test property vs task`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>dependencies") {
        methodTest(resolveTest(PsiMethod::class.java), "getDependencies", GRADLE_API_PROJECT)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "project(':') {<caret>}", 
    "allprojects {<caret>}", 
    "subprojects {<caret>}", 
    "configure(project(':')) {<caret>}",
    "configure([project(':')]) {<caret>}",
    "beforeEvaluate {<caret>}",
    "afterEvaluate {<caret>}"
  """)
  fun `resolve a delegate in Closures of methods providing Project's context`(
    gradleVersion: GradleVersion,
    expression: String
  ) {
    testEmptyProject(gradleVersion) {
      testBuildscript(expression) {
        closureDelegateTest(GRADLE_API_PROJECT, DELEGATE_FIRST)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS, """
    "copy{<caret>}",
    "copySpec{<caret>}"
  """)
  fun `resolve a delegate in copy and copySpec Closures`(gradleVersion: GradleVersion, decorator: String, expression: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, expression) {
        closureDelegateTest(GRADLE_API_FILE_COPY_SPEC, DELEGATE_FIRST)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `resolve a delegate in fileTree Closure`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "fileTree('baseDir'){<caret>}") {
        closureDelegateTest(GRADLE_API_FILE_CONFIGURABLE_FILE_TREE, DELEGATE_FIRST)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `resolve a delegate in files Closure`(gradleVersion: GradleVersion, decorator: String) {
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "files('paths'){<caret>}") {
        closureDelegateTest(GRADLE_API_FILE_CONFIGURABLE_FILE_COLLECTION, DELEGATE_FIRST)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `resolve a delegate in exec Closure`(gradleVersion: GradleVersion, decorator: String) {
    assumeThatGradleIsOlderThan(gradleVersion, "9.0") {
      """
      Project.exec and Project.javaexec were removed in Gradle 9.0.
      See gradle/pull/33141 for more information. 
      """.trimIndent()
    }
    testEmptyProject(gradleVersion) {
      testBuildscript(decorator, "exec{<caret>}") {
        closureDelegateTest(GRADLE_PROCESS_EXEC_SPEC, DELEGATE_FIRST)
      }
    }
  }
}