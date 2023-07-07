// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_JAVA_PLUGIN_CONVENTION
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatTopLevelJavaConventionsIsSupported
import org.junit.jupiter.params.ParameterizedTest

class GradleConventionsTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS, """
    "<caret>docsDir",
    "project.<caret>docsDir"
  """)
  fun `test property read`(gradleVersion: GradleVersion, decorator: String, expression: String) {
    assumeThatTopLevelJavaConventionsIsSupported(gradleVersion)
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, expression) {
        methodTest(resolveTest(PsiMethod::class.java), "getDocsDir", GRADLE_API_JAVA_PLUGIN_CONVENTION)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test property write`(gradleVersion: GradleVersion, decorator: String) {
    assumeThatTopLevelJavaConventionsIsSupported(gradleVersion)
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "<caret>sourceCompatibility = 42") {
        methodTest(resolveTest(PsiMethod::class.java), "setSourceCompatibility", GRADLE_API_JAVA_PLUGIN_CONVENTION)
      }
    }
  }

  // this test is wrong and exists only to preserve current behaviour and to fail when behaviour changes
  @ParameterizedTest
  @AllGradleVersionsSource(DECORATORS)
  fun `test setter method`(gradleVersion: GradleVersion, decorator: String) {
    assumeThatTopLevelJavaConventionsIsSupported(gradleVersion)
    testJavaProject(gradleVersion) {
      testBuildscript(decorator, "<caret>targetCompatibility('1.8')") {
        setterMethodTest("targetCompatibility", "setTargetCompatibility", GRADLE_API_JAVA_PLUGIN_CONVENTION)
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