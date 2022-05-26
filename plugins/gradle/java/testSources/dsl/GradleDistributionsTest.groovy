// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.service.resolve.GradleDomainObjectProperty
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionProperty
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DISTRIBUTION
import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_FILE_COPY_SPEC

@CompileStatic
class GradleDistributionsTest extends GradleHighlightingLightTestCase implements ExpressionTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "distribution")
  }

  @Override
  List<String> getParentCalls() {
//    return []
    return super.getParentCalls() + 'buildscript'
  }

  @Test
  void 'test distributions container'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('<caret>distributions') {
      referenceExpressionTest(GradleExtensionProperty, getDistributionContainerFqn())
    }
  }

  @Test
  void 'test distributions call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('<caret>distributions {}') {
      methodCallTest(PsiMethod, getDistributionContainerFqn())
    }
  }

  @Test
  void 'test distributions closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions { <caret> }') {
      closureDelegateTest(getDistributionContainerFqn(), 1)
    }
  }

  @Test
  void 'test distribution via unqualified property reference'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions { <caret>foo }') {
      referenceExpressionTest(GradleDomainObjectProperty, GRADLE_API_DISTRIBUTION)
    }
  }

  @Test
  void 'test distribution via unqualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions { <caret>foo {} }') {
      methodCallTest(PsiMethod, GRADLE_API_DISTRIBUTION)
    }
  }

  @Test
  void 'test distribution closure delegate in unqualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions { foo { <caret> } }') {
      closureDelegateTest(GRADLE_API_DISTRIBUTION, 1)
    }
  }

  @Test
  void 'test distribution member via unqualified method call closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("distributions { foo { <caret>${getDistributionBaseNameMethod()} } }") {
      def method = resolveMethodTest(PsiMethod)
      assert method.containingClass.qualifiedName == GRADLE_API_DISTRIBUTION
    }
  }

  @Test
  void 'test distribution via qualified property reference'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions.<caret>foo') {
      referenceExpressionTest(GradleDomainObjectProperty, GRADLE_API_DISTRIBUTION)
    }
  }

  @Test
  void 'test distribution via qualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions.<caret>foo {}') {
      methodCallTest(PsiMethod, GRADLE_API_DISTRIBUTION)
    }
  }

  @Test
  void 'test distribution closure delegate in qualified method call'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions.foo { <caret> }') {
      closureDelegateTest(GRADLE_API_DISTRIBUTION, 1)
    }
  }

  @Test
  void 'test distribution member via qualified method call closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("distributions.foo { <caret>${getDistributionBaseNameMethod()} }") {
      def method = resolveMethodTest(PsiMethod)
      assert method.containingClass.qualifiedName == GRADLE_API_DISTRIBUTION
    }
  }

  @Test
  void 'test distribution contents closure delegate'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest('distributions { foo { contents { <caret> } } }') {
      closureDelegateTest(GRADLE_API_FILE_COPY_SPEC, 1)
    }
  }
}
