// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_JAVA_PLUGIN_CONVENTION

@CompileStatic
class GradleConventionsTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "java")
  }

  @Test
  void 'test property read'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('<caret>docsDir') {
      methodTest(resolveTest(PsiMethod), 'getDocsDir', GRADLE_API_JAVA_PLUGIN_CONVENTION)
    }
  }

  @Test
  void 'test property read via project'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('project.<caret>docsDir') {
      methodTest(resolveTest(PsiMethod), 'getDocsDir', GRADLE_API_JAVA_PLUGIN_CONVENTION)
    }
  }

  @Test
  void 'test property write'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('<caret>sourceCompatibility = 42') {
      methodTest(resolveTest(PsiMethod), 'setSourceCompatibility', GRADLE_API_JAVA_PLUGIN_CONVENTION)
    }
  }

  // this test is wrong and exists only to preserve current behaviour and to fail when behaviour changes
  @Test
  void 'test setter method'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('<caret>targetCompatibility("1.8")') {
      setterMethodTest('targetCompatibility', 'setTargetCompatibility', GRADLE_API_JAVA_PLUGIN_CONVENTION)
//      // the correct test is below:
//      def call = elementUnderCaret(GrMethodCall)
//      def result = call.advancedResolve()
//      assert result.invokedOnProperty
//      // getTargetCompatibility() should be resolved, just because it exists, but later it's highlighted with warning
//      methodTest(assertInstanceOf(result.element, PsiMethod), 'getTargetCompatibility', GRADLE_API_JAVA_PLUGIN_CONVENTION)
    }
  }
}
