// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.UsefulTestCase.assertOneElement
import com.intellij.testFramework.assertInstanceOf
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DOMAIN_OBJECT_COLLECTION
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest

class GradleWithGroovyTest : GradleCodeInsightTestCase() {
  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test Project#allprojects call`(gradleVersion: GradleVersion, decorator: String) {
    testGroovyProject(gradleVersion) {
      @Suppress("SpellCheckingInspection")
      testBuildscript(decorator, "<caret>allprojects {}") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val element = assertOneElement(call.multiResolveGroovy(false)).element
        val method = assertInstanceOf<PsiMethod>(element)
        assertEquals(GRADLE_API_PROJECT, method.containingClass!!.qualifiedName)
        assertTrue(method.parameterList.parameters.first().type.equalsToText(GROOVY_LANG_CLOSURE))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  fun `test DomainObjectCollection#all call`(gradleVersion: GradleVersion, decorator: String) {
    testGroovyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>configurations.all {}") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val element = assertOneElement(call.multiResolveGroovy(false)).element
        val method = assertInstanceOf<PsiMethod>(element)
        assertEquals(GRADLE_API_DOMAIN_OBJECT_COLLECTION, method.containingClass!!.qualifiedName)
        assertTrue(method.parameterList.parameters.first().type.equalsToText(GROOVY_LANG_CLOSURE))
      }
    }
  }

  // the test is unstable on TeamCity
  // @ParameterizedTest
  // @AllGradleVersionsSource("$PROJECT_CONTEXTS, buildscript")
  @Disabled("IDEA-375502")
  fun `test DomainObjectCollection#withType call`(gradleVersion: GradleVersion, decorator: String) {
    testGroovyProject(gradleVersion) {
      testBuildscript(decorator, "<caret>plugins.withType(JavaPlugin) {}") {
        val call = elementUnderCaret(GrMethodCall::class.java)
        val element = assertOneElement(call.multiResolveGroovy(false)).element
        val method = assertInstanceOf<PsiMethod>(element)
        assertEquals(GRADLE_API_DOMAIN_OBJECT_COLLECTION, method.containingClass!!.qualifiedName)
        assertTrue(method.parameterList.parameters.last().type.equalsToText(GROOVY_LANG_CLOSURE))
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test DGM#collect`(gradleVersion: GradleVersion) {
    testGroovyProject(gradleVersion) {
      codeInsightFixture.enableInspections(GroovyAssignabilityCheckInspection::class.java)
      testHighlighting("['a', 'b'].collect { it.toUpperCase() }")
    }
  }
}
