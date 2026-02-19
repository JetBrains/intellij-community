// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_JAVA_PLUGIN_EXTENSION
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatJavaConventionsBlockIsSupported
import org.junit.jupiter.params.ParameterizedTest

class GradleJavaExtensionsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS, """
    java."<caret>docsDir",
    "project.java.<caret>docsDir"
  """)
  fun `test property read`(gradleVersion: GradleVersion, decorator: String, expression: String) {
    assumeThatJavaConventionsBlockIsSupported(gradleVersion)
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, expression) {
        methodTest(resolveTest(PsiMethod::class.java), "getDocsDir", GRADLE_API_JAVA_PLUGIN_EXTENSION)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test property write`(gradleVersion: GradleVersion, decorator: String) {
    assumeThatJavaConventionsBlockIsSupported(gradleVersion)
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "java.<caret>sourceCompatibility = 42") {
        methodTest(resolveTest(PsiMethod::class.java), "setSourceCompatibility", GRADLE_API_JAVA_PLUGIN_EXTENSION)
      }
    }
  }

  // this test is wrong and exists only to preserve current behaviour and to fail when behaviour changes
  @ParameterizedTest
  @AllGradleVersionsSource(PROJECT_CONTEXTS)
  fun `test setter method`(gradleVersion: GradleVersion, decorator: String) {
    assumeThatJavaConventionsBlockIsSupported(gradleVersion)
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "java.<caret>targetCompatibility('1.8')") {
        setterMethodTest("targetCompatibility", "setTargetCompatibility", GRADLE_API_JAVA_PLUGIN_EXTENSION)
        //// the correct test is below:
        //val call = elementUnderCaret(GrMethodCall::class.java)
        //val result = call.advancedResolve()
        //assertTrue(result.isInvokedOnProperty)
        //// getTargetCompatibility() should be resolved, just because it exists, but later it's highlighted with warning
        //val method = assertInstanceOf<PsiMethod>(result.element)
        //methodTest(method, "getTargetCompatibility", GRADLE_API_JAVA_PLUGIN_CONVENTION)
      }
    }
  }
}