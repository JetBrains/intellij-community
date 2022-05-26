// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT

@CompileStatic
class GradleArtifactsTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "java")
  }

  @Override
  List<String> getParentCalls() {
    return []
  }

  @Test
  void 'test closure delegate'() {
    doTest('artifacts { <caret> }') {
      closureDelegateTest(GRADLE_API_ARTIFACT_HANDLER, 1)
    }
  }

  @Test
  void 'test member'() {
    doTest('artifacts { <caret>add("conf", "notation") }') {
      methodTest(resolveTest(PsiMethod), "add", GRADLE_API_ARTIFACT_HANDLER)
    }
  }

  @Test
  void 'test unresolved reference'() {
    doTest('artifacts { <caret>foo }', super.getParentCalls()) {
      resolveTest(null)
    }
  }

  @Test
  void 'test unresolved configuration reference'() {
    doTest('artifacts { <caret>archives }') {
      resolveTest(null)
    }
  }

  @Test
  void 'test invalid artifact addition'() {
    // foo configuration doesn't exist
    doTest('artifacts { <caret>foo("artifactNotation") }') {
      assertEmpty(elementUnderCaret(GrMethodCall).multiResolve(false))
    }
  }

  @Test
  void 'test artifact addition'() {
    def test = {
      def call = elementUnderCaret(GrMethodCall)
      def result = assertOneElement(call.multiResolve(false))
      methodTest(assertInstanceOf(result.element, PsiMethod), "archives", GRADLE_API_ARTIFACT_HANDLER)
      assert result.applicable
      assert call.type == PsiType.NULL
    }
    doTest('artifacts { <caret>archives("artifactNotation") }', test)
    doTest('artifacts { <caret>archives("artifactNotation", "artifactNotation2", "artifactNotation3") }', test)
    doTest('artifacts.<caret>archives("artifactNotation")', test)
    doTest('artifacts.<caret>archives("artifactNotation", "artifactNotation2", "artifactNotation3")', test)
  }

  @Test
  void 'test configurable artifact addition'() {
    def test = {
      def call = elementUnderCaret(GrMethodCall)
      def result = assertOneElement(call.multiResolve(false))
      methodTest(assertInstanceOf(result.element, PsiMethod), "archives", GRADLE_API_ARTIFACT_HANDLER)
      assert result.applicable
      assert call.type.equalsToText(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT)
    }
    doTest('artifacts { <caret>archives("artifactNotation") {} }', test)
    doTest('artifacts.<caret>archives("artifactNotation") {}', test)
  }

  @Test
  void 'test configuration delegate'() {
    doTest('artifacts { archives("artifactNotation") { <caret> } }') {
      closureDelegateTest(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT, 1)
    }
  }

  @Test
  void 'test configuration delegate method setter'() {
    doTest('artifacts { archives("artifactNotation") { <caret>name("hi") } }') {
      setterMethodTest('name', 'setName', GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT)
    }
  }
}
