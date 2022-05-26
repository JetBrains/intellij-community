// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_SOURCE_SET
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_SOURCE_SET_CONTAINER

@CompileStatic
class GradleSourceSetsTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "java")
  }

  @Override
  List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  @Test
  void 'test sourceSets closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets { <caret> }') {
      closureDelegateTest(GRADLE_API_SOURCE_SET_CONTAINER, 1)
    }
  }

  @Test
  void 'test source set via unqualified property reference'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets { <caret>main }') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'test source set via unqualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets { <caret>main {} }') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'test source set closure delegate in unqualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets { main { <caret> } }') {
      closureDelegateTest(GRADLE_API_SOURCE_SET, 1)
    }
  }

  @Test
  void 'test source set member via unqualified method call closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets { main { <caret>getJarTaskName() } }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_SOURCE_SET
    }
  }

  @Test
  void 'test source set via qualified property reference'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets.<caret>main') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'test source set via qualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets.<caret>main {}') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'test source set closure delegate in qualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets.main { <caret> }') {
      closureDelegateTest(GRADLE_API_SOURCE_SET, 1)
    }
  }

  @Test
  void 'test source set member via qualified method call closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('sourceSets.main { <caret>getJarTaskName() }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_SOURCE_SET
    }
  }
}
