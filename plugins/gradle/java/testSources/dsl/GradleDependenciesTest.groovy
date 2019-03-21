// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.jetbrains.plugins.gradle.highlighting.GradleHighlightingBaseTest
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER

@CompileStatic
class GradleDependenciesTest extends GradleHighlightingBaseTest implements ResolveTest {

  @Test
  void dependenciesTest() {
    importProject("")
    'dependencies closure delegate'()
  }

  @Override
  protected List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  void 'dependencies closure delegate'() {
    doTest('dependencies { <caret> }') {
      closureDelegateTest(GRADLE_API_DEPENDENCY_HANDLER, 1)
    }
  }
}
