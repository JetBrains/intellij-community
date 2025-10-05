// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.events

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.GradleTestExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.junit.jupiter.params.ParameterizedTest

class SpockGradleTestNavigationTest : GradleTestExecutionTestCase() {

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test display name and navigation with Groovy and Spock`(gradleVersion: GradleVersion) {
    testSpockProject(gradleVersion) {
      writeText("src/test/groovy/org/example/SpockTestCase.groovy", GROOVY_CLASS_WITH_SPOCK_TESTS)

      executeTasks(":test", isRunAsTest = true)
      assertTestViewTree {
        assertNode("SpockTestCase") {
          assertPsiLocation("SpockTestCase")
          assertNode("success test") {
            assertPsiLocation("SpockTestCase", "success test")
          }
          assertNode("failure test") {
            assertPsiLocation("SpockTestCase", "failure test")
          }
          assertNode("length of #name is #length") {
            assertPsiLocation("SpockTestCase", "length of #name is #length")
            assertNode("length of Spock is 5") {
              assertPsiLocation("SpockTestCase", "length of #name is #length")
            }
          }
        }
      }
    }
  }

  @ParameterizedTest
  @AllGradleVersionsSource
  fun `test navigation for inner Groovy class with Spock specification`(gradleVersion: GradleVersion) {
    testSpockProject(gradleVersion) {
      writeText("src/test/groovy/org/example/SpockTestCase.groovy", GROOVY_INNER_CLASS_WITH_SPOCK_TESTS)

      executeTasks(":test --tests 'org.example.SpockTestCase${'$'}InnerTestCase.inner test'", isRunAsTest = true)
      assertTestViewTree {
        assertNode("InnerTestCase") {
          assertPsiLocation("InnerTestCase")
          assertNode("inner test") {
            assertPsiLocation("InnerTestCase", "inner test")
          }
        }
      }
    }
  }

  companion object {

    private val GROOVY_CLASS_WITH_SPOCK_TESTS = """
      |package org.example
      |
      |import spock.lang.Specification
      |
      |class SpockTestCase extends Specification {
      |
      |  def "success test"() {
      |    expect:
      |    true
      |  }
      |
      |  def "failure test"() {
      |    expect:
      |    false
      |  }
      |
      |  def "length of #name is #length"() {
      |    expect:
      |    name.size() != length
      |
      |    where:
      |    name     | length
      |    "Spock"  | 5
      |  }
      |}
    """.trimMargin()

    private val GROOVY_INNER_CLASS_WITH_SPOCK_TESTS = """
      |package org.example
      |
      |import spock.lang.Specification
      |
      |class SpockTestCase {
      |  static class InnerTestCase extends Specification {
      |    def "inner test"() {
      |      expect:
      |        true
      |    }
      |  }
      |}
    """.trimMargin()
  }
}