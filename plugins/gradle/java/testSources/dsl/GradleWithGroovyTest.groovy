// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DOMAIN_OBJECT_COLLECTION
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_PROJECT
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE

@CompileStatic
class GradleWithGroovyTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "groovy")
  }

  @Override
  List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  @Test
  void 'test Project#allprojects call'() {
    doTest('<caret>allprojects {}') {
      def method = assertInstanceOf(assertOneElement(elementUnderCaret(GrMethodCall).multiResolve(false)).element, PsiMethod)
      assert method.containingClass.qualifiedName == GRADLE_API_PROJECT
      assert method.parameterList.parameters.first().type.equalsToText(GROOVY_LANG_CLOSURE)
    }
  }

  @Test
  void 'test DomainObjectCollection#all call'() {
    doTest('<caret>configurations.all {}') {
      def method = assertInstanceOf(assertOneElement(elementUnderCaret(GrMethodCall).multiResolve(false)).element, PsiMethod)
      assert method.containingClass.qualifiedName == GRADLE_API_DOMAIN_OBJECT_COLLECTION
      assert method.parameterList.parameters.first().type.equalsToText(GROOVY_LANG_CLOSURE)
    }
  }

  @Test
  void 'test DomainObjectCollection#withType call'() {
    doTest('<caret>plugins.withType(JavaPlugin) {}') {
      def method = assertInstanceOf(assertOneElement(elementUnderCaret(GrMethodCall).multiResolve(false)).element, PsiMethod)
      assert method.containingClass.qualifiedName == GRADLE_API_DOMAIN_OBJECT_COLLECTION
      assert method.parameterList.parameters.last().type.equalsToText(GROOVY_LANG_CLOSURE)
    }
  }

  @Test
  void 'test DGM#collect'() {
    fixture.enableInspections(GroovyAssignabilityCheckInspection)
    doTestHighlighting '''["a", "b"].collect { it.toUpperCase() }'''
  }
}
