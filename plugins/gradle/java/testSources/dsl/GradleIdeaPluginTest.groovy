// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.util.ExpressionTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleIdeaPluginScriptContributor.*

@CompileStatic
class GradleIdeaPluginTest extends GradleHighlightingLightTestCase implements ExpressionTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "idea")
  }

  @Test
  void 'test idea closure delegate'() {
    doTest('idea { <caret> }') {
      closureDelegateTest(IDEA_MODEL_FQN, 1)
    }
  }

  @Test
  void 'test idea project closure delegate'() {
    doTest('idea { project { <caret> } }') {
      closureDelegateTest(IDEA_PROJECT_FQN, 1)
    }
  }

  @Test
  void 'test idea project ipr closure delegate'() {
    doTest('idea { project { ipr { <caret> } } }') {
      closureDelegateTest(IDE_XML_MERGER_FQN, 1)
    }
  }

  @Test
  void 'test idea module closure delegate'() {
    doTest('idea { module { <caret> } }') {
      closureDelegateTest(IDEA_MODULE_FQN, 1)
    }
  }

  @Test
  void 'test idea module iml closure delegate'() {
    doTest('idea { module { iml { <caret> } } }') {
      closureDelegateTest(IDEA_MODULE_IML_FQN, 1)
    }
  }
}
