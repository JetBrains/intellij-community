// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.events.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.testFramework.gradle.KotlinGradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsOlderThan
import org.junit.jupiter.params.ParameterizedTest

class KotlinGradleTestNavigationTest : KotlinGradleExecutionTestCase() {

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Junit 5 OLD`(gradleVersion: GradleVersion) {
        assumeThatGradleIsOlderThan(gradleVersion, "7.0")
        testKotlinJunit5Project(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_JUNIT5_TEST)
            writeText("src/test/kotlin/org/example/DisplayNameTestCase.kt", KOTLIN_DISPLAY_NAME_JUNIT5_TEST)

            executeTasks(":test", isRunAsTest = true)
            assertTestViewTree {
                assertNode("TestCase") {
                    assertPsiLocation("TestCase")
                    assertNode("test") {
                        assertPsiLocation("TestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("TestCase", "successful test")
                    }
                    assertNode("parametrized test [1] 1, first") {
                        assertPsiLocation("TestCase", "parametrized test", "[1]")
                    }
                    assertNode("parametrized test [2] 2, second") {
                        assertPsiLocation("TestCase", "parametrized test", "[2]")
                    }
                    assertNode("dynamic test dynamic first") {
                        assertPsiLocation("TestCase", "dynamic test", "[1]")
                    }
                    assertNode("dynamic test dynamic second") {
                        assertPsiLocation("TestCase", "dynamic test", "[2]")
                    }
                }
                assertNode("DisplayNameTestCase") {
                    assertPsiLocation("DisplayNameTestCase")
                    assertNode("test") {
                        assertPsiLocation("DisplayNameTestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("DisplayNameTestCase", "successful test")
                    }
                    assertNode("pretty test") {
                        assertPsiLocation("DisplayNameTestCase", "ugly test")
                    }
                    assertNode("ugly parametrized test [1] 3, third") {
                        assertPsiLocation("DisplayNameTestCase", "ugly parametrized test", "[1]")
                    }
                    assertNode("ugly parametrized test [2] 4, fourth") {
                        assertPsiLocation("DisplayNameTestCase", "ugly parametrized test", "[2]")
                    }
                    assertNode("ugly dynamic test dynamic first") {
                        assertPsiLocation("DisplayNameTestCase", "ugly dynamic test", "[1]")
                    }
                    assertNode("ugly dynamic test dynamic second") {
                        assertPsiLocation("DisplayNameTestCase", "ugly dynamic test", "[2]")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Junit 5`(gradleVersion: GradleVersion) {
        assumeThatGradleIsAtLeast(gradleVersion, "7.0")
        testKotlinJunit5Project(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_JUNIT5_TEST)
            writeText("src/test/kotlin/org/example/DisplayNameTestCase.kt", KOTLIN_DISPLAY_NAME_JUNIT5_TEST)

            executeTasks(":test", isRunAsTest = true)
            assertTestViewTree {
                assertNode("TestCase") {
                    assertPsiLocation("TestCase")
                    assertNode("test") {
                        assertPsiLocation("TestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("TestCase", "successful test")
                    }
                    assertNode("parametrized test(int, String)") {
                        assertPsiLocation("TestCase", "parametrized test")
                        assertNode("[1] 1, first") {
                            assertPsiLocation("TestCase", "parametrized test", "[1]")
                        }
                        assertNode("[2] 2, second") {
                            assertPsiLocation("TestCase", "parametrized test", "[2]")
                        }
                    }
                    assertNode("dynamic test") {
                        assertPsiLocation("TestCase", "dynamic test")
                        assertNode("dynamic first") {
                            assertPsiLocation("TestCase", "dynamic test", "[1]")
                        }
                        assertNode("dynamic second") {
                            assertPsiLocation("TestCase", "dynamic test", "[2]")
                        }
                    }
                }
                assertNode("DisplayNameTestCase") {
                    assertPsiLocation("DisplayNameTestCase")
                    assertNode("test") {
                        assertPsiLocation("DisplayNameTestCase", "test")
                    }
                    assertNode("successful test") {
                        assertPsiLocation("DisplayNameTestCase", "successful test")
                    }
                    assertNode("pretty test") {
                        assertPsiLocation("DisplayNameTestCase", "ugly test")
                    }
                    assertNode("pretty parametrized test") {
                        if (isBuiltInTestEventsUsed()) {
                            // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
                            assertPsiLocation("DisplayNameTestCase", "ugly parametrized test")
                        }
                        assertNode("[1] 3, third") {
                            assertPsiLocation("DisplayNameTestCase", "ugly parametrized test", "[1]")
                        }
                        assertNode("[2] 4, fourth") {
                            assertPsiLocation("DisplayNameTestCase", "ugly parametrized test", "[2]")
                        }
                    }
                    assertNode("pretty dynamic test") {
                        if (isBuiltInTestEventsUsed()) {
                            // Known bug. See DefaultGradleTestEventConverter.getConvertedMethodName
                            assertPsiLocation("DisplayNameTestCase", "ugly dynamic test")
                        }
                        assertNode("dynamic first") {
                            assertPsiLocation("DisplayNameTestCase", "ugly dynamic test", "[1]")
                        }
                        assertNode("dynamic second") {
                            assertPsiLocation("DisplayNameTestCase", "ugly dynamic test", "[2]")
                        }
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Junit 4`(gradleVersion: GradleVersion) {
        testKotlinJunit4Project(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_JUNIT4_TEST)
            writeText("src/test/kotlin/org/example/ParametrizedTestCase.kt", KOTLIN_PARAMETRIZED_JUNIT4_TEST)

            executeTasks(":test", isRunAsTest = true)
            assertTestViewTree {
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
                        assertPsiLocation("ParametrizedTestCase", "parametrized test", "[0]")
                    }
                    assertNode("parametrized test[1]") {
                        assertPsiLocation("ParametrizedTestCase", "parametrized test", "[1]")
                    }
                    assertNode("parametrized test[2]") {
                        assertPsiLocation("ParametrizedTestCase", "parametrized test", "[2]")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin Multiplatform and Kotlin Test`(gradleVersion: GradleVersion) {
        assumeThatGradleIsAtLeast(gradleVersion, "6.8.3") {
            "Kotlin multiplatform isn't supported by Gradle older than 6.8.3"
        }
        testKotlinMultiplatformProject(gradleVersion) {
            writeText("src/jsTest/kotlin/Foo.kt", KOTLIN_TEST)

            executeTasks(":jsNodeTest --tests \"Foo\"", isRunAsTest = true)

            assertTestViewTree {
                assertNode("jsNodeTest") {
                    assertNode("Foo") {
                        assertNode("foo[js, node]")
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun `test display name and navigation with Kotlin and Test NG`(gradleVersion: GradleVersion) {
        testKotlinTestNGProject(gradleVersion) {
            writeText("src/test/kotlin/org/example/TestCase.kt", KOTLIN_TESTNG_TEST)
            writeText("src/test/kotlin/org/example/ParametrizedTestCase.kt", KOTLIN_PARAMETRIZED_TESTNG_TEST)

            executeTasks(":test", isRunAsTest = true)
            assertTestViewTree {
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
                                assertPsiLocation("ParametrizedTestCase", "parametrized test", "[0]")
                            }
                            assertNode("parametrized test[1](2, second)") {
                                assertPsiLocation("ParametrizedTestCase", "parametrized test", "[1]")
                            }
                            assertNode("parametrized test[2](3, third)") {
                                assertPsiLocation("ParametrizedTestCase", "parametrized test", "[2]")
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        private val KOTLIN_JUNIT5_TEST = """
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
            |    @ParameterizedTest
            |    @CsvSource("1, 'first'", "2, 'second'")
            |    fun `parametrized test`(value: Int, name: String?) = Unit
            |
            |    @TestFactory
            |    fun `dynamic test`(): List<DynamicTest> {
            |        return listOf(
            |            DynamicTest.dynamicTest("dynamic first") {},
            |            DynamicTest.dynamicTest("dynamic second") {}
            |        )
            |    }
            |}
        """.trimMargin()

        private val KOTLIN_DISPLAY_NAME_JUNIT5_TEST = """
            |package org.example
            |
            |import org.junit.jupiter.api.*
            |import org.junit.jupiter.params.ParameterizedTest
            |import org.junit.jupiter.params.provider.CsvSource
            |
            |class DisplayNameTestCase {
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
            |    @DisplayName("pretty parametrized test")
            |    @CsvSource("3, 'third'", "4, 'fourth'")
            |    fun `ugly parametrized test`(value: Int, name: String?) = Unit
            |
            |    @TestFactory
            |    @DisplayName("pretty dynamic test")
            |    fun `ugly dynamic test`(): List<DynamicTest> {
            |        return listOf(
            |            DynamicTest.dynamicTest("dynamic first") {},
            |            DynamicTest.dynamicTest("dynamic second") {}
            |        )
            |    }
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

        private val KOTLIN_TEST = """
            |import kotlin.test.Test
            |import kotlin.test.assertEquals
            |
            |class Foo {
            |    @Test
            |    fun foo() {
            |        assertEquals(6, 6)
            |    }
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
