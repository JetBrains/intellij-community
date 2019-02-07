// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_SOURCE_SET
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.getDelegatesToInfo

@CompileStatic
class GradleSourceSetsTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Override
  void doTest(@NotNull String text, Closure test) {
    super.doTest("apply plugin: 'java'; $text", test)
  }

  @Test
  void 'sourceSets closure delegate'() {
    doTest('sourceSets { <caret> }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText("org.gradle.api.internal.tasks.DefaultSourceSetContainer")
      assert delegatesToInfo.strategy == 1
    }
  }

  @Test
  void 'source set via unqualified property reference'() {
    doTest('sourceSets { <caret>main }') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText("org.gradle.api.internal.tasks.DefaultSourceSet")
    }
  }

  @Test
  void 'source set via unqualified method call'() {
    doTest('sourceSets { <caret>main {} }') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'source set closure delegate in unqualified method call'() {
    doTest('sourceSets { main { <caret> } }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_SOURCE_SET)
      assert delegatesToInfo.strategy == 1
    }
  }

  @Test
  void 'source set member via unqualified method call closure delegate'() {
    doTest('sourceSets { main { <caret>getJarTaskName() } }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_SOURCE_SET
    }
  }

  @Test
  void 'source set via qualified property reference'() {
    doTest('sourceSets.<caret>main') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'source set via qualified method call'() {
    doTest('sourceSets.<caret>main {}') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_SOURCE_SET)
    }
  }

  @Test
  void 'source set closure delegate in qualified method call'() {
    doTest('sourceSets.main { <caret> }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_SOURCE_SET)
      assert delegatesToInfo.strategy == 1
    }
  }

  @Test
  void 'source set member via qualified method call closure delegate'() {
    doTest('sourceSets.main { <caret>getJarTaskName() }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_SOURCE_SET
    }
  }
}
