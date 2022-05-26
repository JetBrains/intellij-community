// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT

@CompileStatic
class GradleProjectTest extends GradleHighlightingLightTestCase {

  @Test
  void 'test resolve explicit getter'() {
    doTest('<caret>getGroup()') {
      def results = elementUnderCaret(GrMethodCall).multiResolve(false)
      assert results.size() == 1
      def method = assertInstanceOf(results[0].element, PsiMethod)
      assert method.name == 'getGroup'
      assert method.containingClass.qualifiedName == GRADLE_API_PROJECT
    }
  }

  @Test
  void 'test resolve property'() {
    doTest('<caret>group') {
      def results = elementUnderCaret(GrReferenceExpression).multiResolve(false)
      assert results.size() == 1
      def method = assertInstanceOf(results[0].element, PsiMethod)
      assert method.name == 'getGroup'
      assert method.containingClass.qualifiedName == GRADLE_API_PROJECT
    }
  }

  @Test
  void 'test resolve explicit setter'() {
    doTest('<caret>setGroup(1)') {
      def results = elementUnderCaret(GrMethodCall).multiResolve(false)
      assert results.size() == 1
      def method = assertInstanceOf(results[0].element, PsiMethod)
      assert method.name == 'setGroup'
      assert method.containingClass.qualifiedName == GRADLE_API_PROJECT
    }
  }

  @Test
  void 'test resolve explicit setter without argument'() {
    doTest('<caret>setGroup()') {
      def results = elementUnderCaret(GrMethodCall).multiResolve(false)
      assert results.size() == 1
      def method = assertInstanceOf(results[0].element, PsiMethod)
      assert method.name == 'setGroup'
      assert method.containingClass.qualifiedName == GRADLE_API_PROJECT
    }
  }

  @Test
  void 'test resolve property setter'() {
    doTest('<caret>group = 42') {
      def results = elementUnderCaret(GrReferenceExpression).multiResolve(false)
      assert results.size() == 1
      def method = assertInstanceOf(results[0].element, PsiMethod)
      assert method.name == 'setGroup'
      assert method.containingClass.qualifiedName == GRADLE_API_PROJECT
    }
  }

  @Test
  void 'test resolve implicit setter'() {
    doTest('<caret>group(42)') {
      setterMethodTest('group', 'setGroup', GRADLE_API_PROJECT)
    }
  }

  @Test
  void 'test resolve implicit setter without argument'() {
    doTest('<caret>group()') {
      setterMethodTest('group', 'setGroup', GRADLE_API_PROJECT)
    }
  }

  @Test
  @CompileDynamic
  void 'test property vs task'() {
    doTest('<caret>dependencies') {
      methodTest(resolveTest(PsiMethod), "getDependencies", GRADLE_API_PROJECT)
    }
  }
}
