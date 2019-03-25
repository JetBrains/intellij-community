// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import com.intellij.testFramework.RunAll
import groovy.transform.CompileStatic
import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER

@CompileStatic
class GradleArtifactsTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Test
  void artifactsTest() {
    importProject("apply plugin: 'java'")
    new RunAll().append {
      'artifacts closure delegate'()
    } append {
      'artifacts member'()
    } append {
      'artifacts unresolved reference'()
    } run()
  }

  @Override
  protected List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  void 'artifacts closure delegate'() {
    doTest('artifacts { <caret> }') {
      closureDelegateTest(GRADLE_API_ARTIFACT_HANDLER, 1)
    }
  }

  void 'artifacts member'() {
    doTest('artifacts { <caret>add("conf", "notation") }') {
      methodTest(resolveTest(PsiMethod), "add", GRADLE_API_ARTIFACT_HANDLER)
    }
  }

  void 'artifacts unresolved reference'() {
    doTest('artifacts { <caret>foo }') {
      resolveTest(null)
    }
  }
}
