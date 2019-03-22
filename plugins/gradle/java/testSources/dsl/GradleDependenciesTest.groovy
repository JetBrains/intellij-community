// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunAll
import groovy.transform.CompileStatic
import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*

@CompileStatic
class GradleDependenciesTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Override
  protected List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  @Test
  void dependenciesTest() {
    importProject("")
    new RunAll().append {
      'dependencies delegate'()
    } append {
      'add delegate'()
    } append {
      'add delegate method setter'()
    } append {
      'module delegate'()
    } append {
      'module delegate method setter'()
    } append {
      'components delegate'()
    } append {
      'modules delegate'()
    } append {
      'modules module delegate'()
    } run()
  }

  void 'dependencies delegate'() {
    doTest('dependencies { <caret> }') {
      closureDelegateTest(GRADLE_API_DEPENDENCY_HANDLER, 1)
    }
  }

  void 'add delegate'() {
    doTest('dependencies { add("compile", "notation") {<caret>} }') {
      closureDelegateTest(GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY, 1)
    }
  }

  void 'add delegate method setter'() {
    doTest('dependencies { add("compile", "notation") { <caret>transitive(false) } }') {
      def result = elementUnderCaret(GrMethodCall).advancedResolve()
      def method = assertInstanceOf(result.element, PsiMethod)
      methodTest(method, 'transitive', GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY)
      def original = assertInstanceOf(method.navigationElement, PsiMethod)
      methodTest(original, 'setTransitive', GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY)
    }
  }

  void 'module delegate'() {
    doTest('dependencies { module(":") {<caret>} }') {
      closureDelegateTest(GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY, 1)
    }
  }

  void 'module delegate method setter'() {
    doTest('dependencies { module(":") { <caret>changing(true) } }') {
      def result = elementUnderCaret(GrMethodCall).advancedResolve()
      def method = assertInstanceOf(result.element, PsiMethod)
      methodTest(method, 'changing', GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
      def original = assertInstanceOf(method.navigationElement, PsiMethod)
      methodTest(original, 'setChanging', GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
    }
  }

  void 'components delegate'() {
    doTest('dependencies { components {<caret>} }') {
      closureDelegateTest(GRADLE_API_COMPONENT_METADATA_HANDLER, 1)
    }
  }

  void 'modules delegate'() {
    doTest('dependencies { modules {<caret>} }') {
      closureDelegateTest(GRADLE_API_COMPONENT_MODULE_METADATA_HANDLER, 1)
    }
  }

  void 'modules module delegate'() {
    doTest('dependencies { modules { module(":") { <caret> } } }') {
      closureDelegateTest(GRADLE_API_COMPONENT_MODULE_METADATA_DETAILS, 1)
    }
  }
}
