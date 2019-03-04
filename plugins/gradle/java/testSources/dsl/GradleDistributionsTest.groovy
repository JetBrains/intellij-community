// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DISTRIBUTION
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_FILE_COPY_SPEC
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.getDelegatesToInfo

@CompileStatic
class GradleDistributionsTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Test
  void distributionsTest() {
    importProject("apply plugin: 'distribution'")
    'distributions closure delegate'()
    'distribution via unqualified property reference'()
    'distribution via unqualified method call'()
    'distribution closure delegate in unqualified method call'()
    'distribution member via unqualified method call closure delegate'()
    'distribution via qualified property reference'()
    'distribution via qualified method call'()
    'distribution closure delegate in qualified method call'()
    'distribution member via qualified method call closure delegate'()
    'distribution contents closure delegate'()
  }

  @Override
  protected List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  void 'distributions closure delegate'() {
    doTest('distributions { <caret> }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText("org.gradle.api.distribution.internal.DefaultDistributionContainer")
      assert delegatesToInfo.strategy == 1
    }
  }

  void 'distribution via unqualified property reference'() {
    doTest('distributions { <caret>foo }') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText("org.gradle.api.distribution.internal.DefaultDistribution")
    }
  }

  void 'distribution via unqualified method call'() {
    doTest('distributions { <caret>foo {} }') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_DISTRIBUTION)
    }
  }

  void 'distribution closure delegate in unqualified method call'() {
    doTest('distributions { foo { <caret> } }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_DISTRIBUTION)
      assert delegatesToInfo.strategy == 1
    }
  }

  void 'distribution member via unqualified method call closure delegate'() {
    doTest('distributions { foo { <caret>getBaseName() } }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_DISTRIBUTION
    }
  }

  void 'distribution via qualified property reference'() {
    doTest('distributions { foo }; distributions.<caret>foo') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText(GRADLE_API_DISTRIBUTION)
    }
  }

  void 'distribution via qualified method call'() {
    doTest('distributions { foo }; distributions.<caret>foo {}') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_DISTRIBUTION)
    }
  }

  void 'distribution closure delegate in qualified method call'() {
    doTest('distributions { foo }; distributions.foo { <caret> }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_DISTRIBUTION)
      assert delegatesToInfo.strategy == 1
    }
  }

  void 'distribution member via qualified method call closure delegate'() {
    doTest('distributions { foo }; distributions.foo { <caret>getBaseName() }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_DISTRIBUTION
    }
  }

  void 'distribution contents closure delegate'() {
    doTest('distributions { foo { contents { <caret> } } }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_FILE_COPY_SPEC)
      assert delegatesToInfo.strategy == 1
    }
  }
}
