// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURATION
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURATION_CONTAINER
import static org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt.getDelegatesToInfo

@CompileStatic
class GradleConfigurationsTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Test
  void 'configurations closure delegate'() {
    doTest('configurations { <caret> }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_CONFIGURATION_CONTAINER)
      assert delegatesToInfo.strategy == 1
    }
  }

  @Test
  void 'configuration via unqualified property reference'() {
    doTest('configurations { <caret>foo }') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText(GRADLE_API_CONFIGURATION)
    }
  }

  @Test
  void 'configuration via unqualified method call'() {
    doTest('configurations { <caret>foo {} }') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_CONFIGURATION)
    }
  }

  @Test
  void 'configuration closure delegate in unqualified method call'() {
    doTest('configurations { foo { <caret> } }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_CONFIGURATION)
      assert delegatesToInfo.strategy == 1
    }
  }

  @Test
  void 'configuration member via unqualified method call closure delegate'() {
    doTest('configurations { foo { <caret>extendsFrom() } }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_CONFIGURATION
    }
  }

  @Test
  void 'configuration via qualified property reference'() {
    doTest('configurations { foo }; configurations.<caret>foo') {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() != null
      assert ref.type.equalsToText(GRADLE_API_CONFIGURATION)
    }
  }

  @Test
  void 'configuration via qualified method call'() {
    doTest('configurations { foo }; configurations.<caret>foo {}') {
      def call = elementUnderCaret(GrMethodCall)
      assert call.resolveMethod() != null
      assert call.type.equalsToText(GRADLE_API_CONFIGURATION)
    }
  }

  @Test
  void 'configuration closure delegate in qualified method call'() {
    doTest('configurations { foo }; configurations.foo { <caret> }') {
      def closure = elementUnderCaret(GrClosableBlock)
      def delegatesToInfo = getDelegatesToInfo(closure)
      assert delegatesToInfo.typeToDelegate.equalsToText(GRADLE_API_CONFIGURATION)
      assert delegatesToInfo.strategy == 1
    }
  }

  @Test
  void 'configuration member via qualified method call closure delegate'() {
    doTest('configurations { foo }; configurations.foo { <caret>extendsFrom() }') {
      def call = elementUnderCaret(GrMethodCall)
      def method = call.resolveMethod()
      assert method != null
      assert method.containingClass.qualifiedName == GRADLE_API_CONFIGURATION
    }
  }
}
