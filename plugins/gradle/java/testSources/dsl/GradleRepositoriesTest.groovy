// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl


import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*

@CompileStatic
class GradleRepositoriesTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  List<String> getParentCalls() {
    return super.getParentCalls() + 'buildscript'
  }

  @Test
  void 'test repositories closure delegate'() {
    doTest('repositories { <caret> }') {
      closureDelegateTest(GRADLE_API_REPOSITORY_HANDLER, 1)
    }
  }

  @Test
  void 'test maven repository closure delegate'() {
    doTest('repositories { maven { <caret> } }') {
      closureDelegateTest(GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY, 1)
    }
  }

  @Test
  void 'test ivy repository closure delegate'() {
    doTest('repositories { ivy { <caret> } }') {
      closureDelegateTest(GRADLE_API_ARTIFACTS_REPOSITORIES_IVY_ARTIFACT_REPOSITORY, 1)
    }
  }

  @Test
  void 'test flat repository closure delegate'() {
    doTest('repositories { flatDir { <caret> } }') {
      closureDelegateTest(GRADLE_API_ARTIFACTS_REPOSITORIES_FLAT_DIRECTORY_ARTIFACT_REPOSITORY, 1)
    }
  }

  @Test
  void 'test maven repository method setter'() {
    doTest('repositories { maven { <caret>url(42) } }') {
      setterMethodTest('url', 'setUrl', GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY)
    }
  }

  @Test
  void 'test ivy repository method setter'() {
    doTest('repositories { ivy { <caret>url("") } }') {
      setterMethodTest('url', 'setUrl', GRADLE_API_ARTIFACTS_REPOSITORIES_IVY_ARTIFACT_REPOSITORY)
    }
  }

  @Test
  void 'test flat repository method setter'() {
    doTest('repositories { flatDir { <caret>name("") } }') {
      setterMethodTest('name', 'setName', GRADLE_API_ARTIFACTS_REPOSITORIES_ARTIFACT_REPOSITORY)
    }
  }
}
