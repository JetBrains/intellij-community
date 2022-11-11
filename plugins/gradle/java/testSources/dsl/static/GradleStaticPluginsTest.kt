// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl.static

import com.intellij.openapi.externalSystem.util.runReadAction
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.params.ParameterizedTest

class GradleStaticPluginsTest : GradleCodeInsightTestCase() {

  private val JAVA_PLUGIN = "plugins { id 'java' }"

  @ParameterizedTest
  @BaseGradleVersionSource()
  fun testJavaPluginConfiguration(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      updateProjectFile("$JAVA_PLUGIN; dependencies { implementati<caret>on() }")
      runReadAction {
        assertNotNull(elementUnderCaret(GrReferenceElement::class.java).resolve())
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource()
  fun testJavaPluginExtension(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      updateProjectFile("$JAVA_PLUGIN; jav<caret>a")
      runReadAction {
        assertNotNull(elementUnderCaret(GrReferenceElement::class.java).resolve())
      }
    }
  }

  @ParameterizedTest
  @BaseGradleVersionSource()
  fun testJavaPluginTask(gradleVersion: GradleVersion) {
    testEmptyProject(gradleVersion) {
      updateProjectFile("$JAVA_PLUGIN; java<caret>doc")
      runReadAction {
        assertNotNull(elementUnderCaret(GrReferenceExpression::class.java).resolve())
      }
    }
  }
}