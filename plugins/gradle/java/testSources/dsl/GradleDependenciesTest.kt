// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.dsl

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import com.intellij.psi.PsiMethod
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.testFramework.GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.junit.jupiter.params.ParameterizedTest

class GradleDependenciesTest : GradleCodeInsightTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test dependencies delegate`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { <caret> }") {
        closureDelegateTest(GRADLE_API_DEPENDENCY_HANDLER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "dependencies { add('archives', name: 42) { <caret> } }",
    "dependencies { add('archives', [name:42]) { <caret> } }",
    "dependencies { add('archives', ':42') { <caret> } }",
    "dependencies { archives(name: 42) { <caret> } }",
    "dependencies { archives([name:42]) { <caret> } }",
    "dependencies { archives(':42') { <caret> } }",
    "dependencies.add('archives', name: 42) { <caret> }",
    "dependencies.add('archives', [name:42]) { <caret> }",
    "dependencies.add('archives', ':42') { <caret> }",
    "dependencies.archives(name: 42) { <caret> }",
    "dependencies.archives([name:42]) { <caret> }",
    "dependencies.archives(':42') { <caret> }"
  """)
  fun `test add external module dependency delegate`(gradleVersion: GradleVersion, expression: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(expression) {
        closureDelegateTest(GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
    "dependencies { add('archives', files()) { <caret> } }",
    "dependencies { add('archives', fileTree("libs")) { <caret> } }",
    "dependencies { archives(files()) { <caret> } }",
    "dependencies { archives(fileTree('libs')) { <caret> } }",
    "dependencies.add('archives', files()) { <caret> }",
    "dependencies.add('archives', fileTree('libs')) { <caret> }",
    "dependencies.archives(files()) { <caret> }",
    "dependencies.archives(fileTree('libs')) { <caret> }"
  """)
  fun `test add self resolving dependency delegate`(gradleVersion: GradleVersion, expression: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(expression) {
        closureDelegateTest(GRADLE_API_ARTIFACTS_SELF_RESOLVING_DEPENDENCY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource("""
      "dependencies { add('archives', project(':')) { <caret> } }",
      "dependencies { archives(project(':')) { <caret> } }",
      "dependencies.add('archives', project(':')) { <caret> }",
      "dependencies.archives(project(':')) { <caret> }"
  """)
  fun `test add project dependency delegate`(gradleVersion: GradleVersion, expression: String) {
    testJavaProject(gradleVersion) {
      testBuildscript(expression) {
        closureDelegateTest(GRADLE_API_ARTIFACTS_PROJECT_DEPENDENCY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test add delegate method setter`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { add('archives', 'notation') { <caret>transitive(false) } }") {
        setterMethodTest("transitive", "setTransitive", GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test module delegate`(gradleVersion: GradleVersion) {
    assumeThatGradleIsOlderThan(gradleVersion, "9.0") {
      """
      ClientModule dependencies were a legacy precursor to ComponentMetadataRules, and have since been replaced and removed in Gradle 9.0.
      See gradle/pull/32743 for more information. 
      """.trimIndent()
    }
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { module(':') {<caret>} }") {
        closureDelegateTest(GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test module delegate method setter`(gradleVersion: GradleVersion) {
    assumeThatGradleIsOlderThan(gradleVersion, "9.0") {
      """
      ClientModule dependencies were a legacy precursor to ComponentMetadataRules, and have since been replaced and removed in Gradle 9.0.
      See gradle/pull/32743 for more information. 
      """.trimIndent()
    }
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { module(':') { <caret>changing(true) } }") {
        setterMethodTest("changing", "setChanging", GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test components delegate`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { components {<caret>} }") {
        closureDelegateTest(GRADLE_API_COMPONENT_METADATA_HANDLER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test modules delegate`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { modules {<caret>} }") {
        closureDelegateTest(GRADLE_API_COMPONENT_MODULE_METADATA_HANDLER, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test modules module delegate`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { modules { module(':') { <caret> } } }") {
        val name = if (GradleVersionUtil.isGradleAtLeast(gradleVersion, "5.0"))
          GRADLE_API_COMPONENT_MODULE_METADATA_DETAILS
        else GRADLE_API_COMPONENT_MODULE_METADATA
        closureDelegateTest(name, 1)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test classpath configuration`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { <caret>classpath('hi') }") {
        resolveTest<Nothing>(null)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test archives configuration`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies { <caret>archives('hi') }") {
        methodTest(resolveTest(PsiMethod::class.java), "archives", GRADLE_API_DEPENDENCY_HANDLER)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test archives configuration via property`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("dependencies.<caret>archives('hi')") {
        methodTest(resolveTest(PsiMethod::class.java), "archives", GRADLE_API_DEPENDENCY_HANDLER)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test buildscript classpath configuration`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("buildscript { dependencies { <caret>classpath('hi') } }") {
        methodTest(resolveTest(PsiMethod::class.java), "classpath", GRADLE_API_DEPENDENCY_HANDLER)
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test buildscript archives configuration`(gradleVersion: GradleVersion) {
    testJavaProject(gradleVersion) {
      testBuildscript("buildscript { dependencies { <caret>archives('hi') } }") {
        resolveTest<Nothing>(null)
      }
    }
  }
}