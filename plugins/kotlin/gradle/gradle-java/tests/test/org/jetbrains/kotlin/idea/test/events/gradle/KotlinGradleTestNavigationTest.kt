// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.events.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class KotlinGradleTestNavigationTest : KotlinGradleExecutionTestCase() {

    @ParameterizedTest
    @TargetVersions("5.6.2 <=> 7.0")
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Junit 5 OLD`(gradleVersion: GradleVersion) {
        testKotlinJunit5Project(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_CLASS_WITH_PARAMETRISED_JUNIT5_TESTS)

            executeTasks(":test")
            assertTestTreeView {
                assertNode("TestCase") {
                    assertPsiLocation("TestCase")
                    assertNode("test") {
                        assertPsiLocation("TestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("TestCase", "successful test")
                    }
                    assertNode("pretty test") {
                        assertPsiLocation("TestCase", "ugly test")
                    }
                    assertNode("parametrized test [1] 1, first") {
                        assertPsiLocation("TestCase", "parametrized test")
                    }
                    assertNode("parametrized test [2] 2, second") {
                        assertPsiLocation("TestCase", "parametrized test")
                    }
                    assertNode("ugly parametrized test [1] 3, third") {
                        assertPsiLocation("TestCase", "ugly parametrized test")
                    }
                    assertNode("ugly parametrized test [2] 4, fourth") {
                        assertPsiLocation("TestCase", "ugly parametrized test")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @TargetVersions("7.0+")
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Junit 5`(gradleVersion: GradleVersion) {
        testKotlinJunit5Project(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_CLASS_WITH_PARAMETRISED_JUNIT5_TESTS)

            executeTasks(":test")
            assertTestTreeView {
                assertNode("TestCase") {
                    assertPsiLocation("TestCase")
                    assertNode("test") {
                        assertPsiLocation("TestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("TestCase", "successful test")
                    }
                    assertNode("pretty test") {
                        assertPsiLocation("TestCase", "ugly test")
                    }
                    assertNode("parametrized test") {
                        if (isSupportedTestLauncher()) {
                            // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
                            assertPsiLocation("TestCase", "parametrized test")
                        }
                        assertNode("[1] 1, first") {
                            assertPsiLocation("TestCase", "parametrized test")
                        }
                        assertNode("[2] 2, second") {
                            assertPsiLocation("TestCase", "parametrized test")
                        }
                    }
                    assertNode("pretty parametrized test") {
                        if (isSupportedTestLauncher()) {
                            // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
                            assertPsiLocation("TestCase", "ugly parametrized test")
                        }
                        assertNode("[1] 3, third") {
                            assertPsiLocation("TestCase", "ugly parametrized test")
                        }
                        assertNode("[2] 4, fourth") {
                            assertPsiLocation("TestCase", "ugly parametrized test")
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @TargetVersions("5.6.2+")
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Junit 4`(gradleVersion: GradleVersion) {
        testKotlinJunit4Project(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_JUNIT4_TEST)
            writeText("src/test/kotlin/org/example/ParametrizedTestCase.kt", KOTLIN_PARAMETRIZED_JUNIT4_TEST)

            executeTasks(":test")
            assertTestTreeView {
                assertNode("TestCase") {
                    assertPsiLocation("TestCase")
                    assertNode("test") {
                        assertPsiLocation("TestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("TestCase", "successful test")
                    }
                }
                assertNode("ParametrizedTestCase") {
                    assertPsiLocation("ParametrizedTestCase")
                    assertNode("parametrized test[0]") {
                        assertPsiLocation("ParametrizedTestCase", "parametrized test")
                    }
                    assertNode("parametrized test[1]") {
                        assertPsiLocation("ParametrizedTestCase", "parametrized test")
                    }
                    assertNode("parametrized test[2]") {
                        assertPsiLocation("ParametrizedTestCase", "parametrized test")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @TargetVersions("5.6.2+")
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Test NG`(gradleVersion: GradleVersion) {
        testKotlinTestNGProject(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_TESTNG_TEST)
            writeText("src/test/kotlin/org/example/ParametrizedTestCase.kt", KOTLIN_PARAMETRIZED_TESTNG_TEST)

            executeTasks(":test")
            assertTestTreeView {
                assertNode("Gradle suite") {
                    assertNode("Gradle test") {
                        assertNode("TestCase") {
                            assertPsiLocation("TestCase")
                            assertNode("test") {
                                assertPsiLocation("TestCase", "test")
                            }
                            assertNode("successful test") {
                                assertPsiLocation("TestCase", "successful test")
                            }
                        }
                        assertNode("ParametrizedTestCase") {
                            assertPsiLocation("ParametrizedTestCase")
                            assertNode("parametrized test[0](1, first)") {
                                assertPsiLocation("ParametrizedTestCase", "parametrized test")
                            }
                            assertNode("parametrized test[1](2, second)") {
                                assertPsiLocation("ParametrizedTestCase", "parametrized test")
                            }
                            assertNode("parametrized test[2](3, third)") {
                                assertPsiLocation("ParametrizedTestCase", "parametrized test")
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        private val KOTLIN_CLASS_WITH_PARAMETRISED_JUNIT5_TESTS = """
            |package org.example
            |
            |import org.junit.jupiter.api.*
            |import org.junit.jupiter.params.ParameterizedTest
            |import org.junit.jupiter.params.provider.CsvSource
            |
            |class TestCase {
            |
            |    @Test
            |    fun test() = Unit
            |
            |    @Test
            |    fun `successful test`() = Unit
            |
            |    @Test
            |    @DisplayName("pretty test")
            |    fun `ugly test`() = Unit
            |
            |    @ParameterizedTest
            |    @CsvSource("1, 'first'", "2, 'second'")
            |    fun `parametrized test`(value: Int, name: String?) = Unit
            |
            |    @ParameterizedTest
            |    @DisplayName("pretty parametrized test")
            |    @CsvSource("3, 'third'", "4, 'fourth'")
            |    fun `ugly parametrized test`(value: Int, name: String?) = Unit
            |}
        """.trimMargin()

        private val KOTLIN_JUNIT4_TEST = """
            |package org.example
            |
            |import org.junit.Assert
            |import org.junit.Ignore
            |import org.junit.Test
            |
            |class TestCase {
            |
            |    @Test
            |    fun test() = Unit
            |
            |    @Test
            |    fun `successful test`() = Unit
            |}
        """.trimMargin()

        private val KOTLIN_PARAMETRIZED_JUNIT4_TEST = """
            |package org.example
            |
            |import org.junit.Test
            |import org.junit.runner.RunWith
            |import org.junit.runners.Parameterized
            |
            |@RunWith(Parameterized::class)
            |class ParametrizedTestCase(
            |    private val value: Int,
            |    private val name: String
            |) {
            |
            |    @Test
            |    fun `parametrized test`() = Unit
            |
            |    companion object {
            |
            |        @JvmStatic
            |        @Parameterized.Parameters
            |        fun data() = listOf(
            |            arrayOf(1, "first"),
            |            arrayOf(2, "second"),
            |            arrayOf(3, "third")
            |        )
            |    }
            |}
        """.trimMargin()

        private val KOTLIN_TESTNG_TEST = """
            |package org.example
            |
            |import org.testng.annotations.Ignore
            |import org.testng.annotations.Test
            |
            |class TestCase {
            |
            |    @Test
            |    fun test() = Unit
            |
            |    @Test
            |    fun `successful test`() = Unit
            |}
        """.trimMargin()

        private val KOTLIN_PARAMETRIZED_TESTNG_TEST = """
            |package org.example
            |
            |import org.testng.annotations.DataProvider
            |import org.testng.annotations.Test
            |
            |class ParametrizedTestCase {
            |
            |    @Test(dataProvider = "data")
            |    fun `parametrized test`(value: Int, name: String) = Unit
            |
            |    companion object {
            |
            |        @JvmStatic
            |        @DataProvider(name = "data")
            |        fun data() = arrayOf(
            |            arrayOf(1, "first"),
            |            arrayOf(2, "second"),
            |            arrayOf(3, "third")
            |        )
            |    }
            |}
        """.trimMargin()
    }
}
