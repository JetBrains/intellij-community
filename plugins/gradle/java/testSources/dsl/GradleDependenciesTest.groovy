// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.psi.PsiMethod
import groovy.transform.CompileStatic
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixture
import org.jetbrains.plugins.groovy.util.ResolveTest
import org.junit.Test

import static org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*

@CompileStatic
class GradleDependenciesTest extends GradleHighlightingLightTestCase implements ResolveTest {

  @Override
  GradleTestFixture createGradleTestFixture(@NotNull GradleVersion gradleVersion) {
    return createGradleTestFixture(gradleVersion, "java")
  }

  @Override
  List<String> getParentCalls() {
    return []
  }

  @Test
  void 'test dependencies delegate'() {
    doTest('dependencies { <caret> }') {
      closureDelegateTest(GRADLE_API_DEPENDENCY_HANDLER, 1)
    }
  }

  @Test
  void 'test add external module dependency delegate'() {
    def data = [
      'dependencies { add("archives", name: 42) { <caret> } }',
      'dependencies { add("archives", [name:42]) { <caret> } }',
      'dependencies { add("archives", ":42") { <caret> } }',
      'dependencies { archives(name: 42) { <caret> } }',
      'dependencies { archives([name:42]) { <caret> } }',
      'dependencies { archives(":42") { <caret> } }',
      'dependencies.add("archives", name: 42) { <caret> }',
      'dependencies.add("archives", [name:42]) { <caret> }',
      'dependencies.add("archives", ":42") { <caret> }',
      'dependencies.archives(name: 42) { <caret> }',
      'dependencies.archives([name:42]) { <caret> }',
      'dependencies.archives(":42") { <caret> }',
    ]
    doTest(data) {
      closureDelegateTest(GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY, 1)
    }
  }

  @Test
  void 'test add self resolving dependency delegate'() {
    def data = [
      'dependencies { add("archives", files()) { <caret> } }',
      'dependencies { add("archives", fileTree("libs")) { <caret> } }',
      'dependencies { archives(files()) { <caret> } }',
      'dependencies { archives(fileTree("libs")) { <caret> } }',
      'dependencies.add("archives", files()) { <caret> }',
      'dependencies.add("archives", fileTree("libs")) { <caret> }',
      'dependencies.archives(files()) { <caret> }',
      'dependencies.archives(fileTree("libs")) { <caret> }',
    ]
    doTest(data) {
      closureDelegateTest(GRADLE_API_ARTIFACTS_SELF_RESOLVING_DEPENDENCY, 1)
    }
  }

  @Test
  void 'test add project dependency delegate'() {
    def data = [
      'dependencies { add("archives", project(":")) { <caret> } }',
      'dependencies { archives(project(":")) { <caret> } }',
      'dependencies.add("archives", project(":")) { <caret> }',
      'dependencies.archives(project(":")) { <caret> }',
    ]
    doTest(data) {
      closureDelegateTest(GRADLE_API_ARTIFACTS_PROJECT_DEPENDENCY, 1)
    }
  }

  @Test
  void 'test add delegate method setter'() {
    doTest('dependencies { add("archives", "notation") { <caret>transitive(false) } }') {
      setterMethodTest('transitive', 'setTransitive', GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY)
    }
  }

  @Test
  void 'test module delegate'() {
    doTest('dependencies { module(":") {<caret>} }') {
      closureDelegateTest(GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY, 1)
    }
  }

  @Test
  void 'test module delegate method setter'() {
    doTest('dependencies { module(":") { <caret>changing(true) } }') {
      setterMethodTest('changing', 'setChanging', GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
    }
  }

  @Test
  void 'test components delegate'() {
    doTest('dependencies { components {<caret>} }') {
      closureDelegateTest(GRADLE_API_COMPONENT_METADATA_HANDLER, 1)
    }
  }

  @Test
  void 'test modules delegate'() {
    doTest('dependencies { modules {<caret>} }') {
      closureDelegateTest(GRADLE_API_COMPONENT_MODULE_METADATA_HANDLER, 1)
    }
  }

  @Test
  void 'test modules module delegate'() {
    doTest('dependencies { modules { module(":") { <caret> } } }') {
      closureDelegateTest(GRADLE_API_COMPONENT_MODULE_METADATA_DETAILS, 1)
    }
  }

  @Test
  void 'test classpath configuration'() {
    doTest('dependencies { <caret>classpath("hi") }') {
      resolveTest(null)
    }
  }

  @Test
  void 'test archives configuration'() {
    doTest('dependencies { <caret>archives("hi") }') {
      methodTest(resolveTest(PsiMethod), "archives", GRADLE_API_DEPENDENCY_HANDLER)
    }
  }

  @Test
  void 'test archives configuration via property'() {
    doTest('dependencies.<caret>archives("hi")') {
      methodTest(resolveTest(PsiMethod), "archives", GRADLE_API_DEPENDENCY_HANDLER)
    }
  }

  @Test
  void 'test buildscript classpath configuration'() {
    doTest('buildscript { dependencies { <caret>classpath("hi") } }') {
      methodTest(resolveTest(PsiMethod), "classpath", GRADLE_API_DEPENDENCY_HANDLER)
    }
  }

  @Test
  void 'test buildscript archives configuration'() {
    doTest('buildscript { dependencies { <caret>archives("hi") } }') {
      resolveTest(null)
    }
  }
}
