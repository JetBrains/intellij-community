// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.service.resolve.GradleGroovyProperty
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyProperty
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.Test

import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import static org.jetbrains.plugins.gradle.testFramework.util.GradleFileTestUtil.withBuildFile
import static org.jetbrains.plugins.gradle.testFramework.util.GradleFileTestUtil.withSettingsFile

@CompileStatic
class GradleExtensionsTest extends GradleHighlightingLightTestCase implements ExpressionTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return GradleTestFixtureFactory.fixtureFactory
      .createGradleTestFixture("property-project", gradleVersion) {
        withSettingsFile(it) {
          it.setProjectName("property-project")
        }
        withBuildFile(it, '''
          |ext {
          |    prop = 1
          |}'''.stripMargin())
      }
  }

  List<String> getParentCalls() {
    // todo resolve extensions also for non-root places
    return []
  }

  @Test
  void 'test project level extension property'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("ext") {
      def ref = elementUnderCaret(GrReferenceExpression)
      assert ref.resolve() instanceof GroovyProperty
      assert ref.type.equalsToText(getExtraPropertiesExtensionFqn())
    }
  }

  @Test
  void 'test project level extension call type'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("ext {}") {
      def call = elementUnderCaret(GrMethodCallExpression)
      assert call.resolveMethod() instanceof GrMethod
      assert call.type.equalsToText(getExtraPropertiesExtensionFqn())
    }
  }

  @Test
  void 'test project level extension closure delegate type'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("ext {<caret>}") {
      closureDelegateTest(getExtraPropertiesExtensionFqn(), 1)
    }
  }

  @Test
  void 'test property reference'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("<caret>prop") {
      referenceExpressionTest(GradleGroovyProperty, JAVA_LANG_INTEGER)
    }
  }

  @Test
  void 'test property reference via project'() {
    reloadProject() // Todo: remove when https://youtrack.jetbrains.com/issue/IDEA-295016 is fixed
    doTest("project.<caret>prop") {
      referenceExpressionTest(GradleGroovyProperty, JAVA_LANG_INTEGER)
    }
  }
}
