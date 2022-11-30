// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.testIntegration

import com.intellij.execution.junit.TestDiffUpdateTest
import org.intellij.lang.annotations.Language

class KotlinTestDiffUpdateTest : TestDiffUpdateTest() {
    @Suppress("SameParameterValue")
    private fun checkAcceptDiff(
        @Language("kotlin") before: String,
        @Language("kotlin") after: String,
        testClass: String,
        testName: String,
        expected: String,
        actual: String,
        stackTrace: String
    ) = checkAcceptDiff(before, after, testClass, testName, expected, actual, stackTrace, "kt")

    fun `test string literal diff`() {
        checkAcceptDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected", "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("actual", "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test parameter reference diff`() {
        checkAcceptDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    doTest("expected")
                }
            
                private fun doTest(ex: String) {
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    doTest("actual")
                }
            
                private fun doTest(ex: String) {
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.doTest(MyJUnitTest.kt:11)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `_test parameter reference diff multiple methods on same line`() {
        checkAcceptDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    doAnotherTest(); doTest("expected")
                }
            
                private fun doTest(ex: String) {
                    Assert.assertEquals(ex, "actual")
                }
                
                private fun doAnotherTest() { } 
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    doAnotherTest(); doTest("actual")
                }
            
                private fun doTest(ex: String) {
                    Assert.assertEquals(ex, "actual")
                }
                
                private fun doAnotherTest() { } 
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.doTest(MyJUnitTest.kt:11)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test local variable reference diff`() {
        checkAcceptDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    val ex = "expected"
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    val ex = "actual"
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:8)
        """.trimIndent()
        )
    }

    fun `test field reference diff`() {
        checkAcceptDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                private val ex = "expected"
            
                @Test
                fun testFoo() {
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                private val ex = "actual"
            
                @Test
                fun testFoo() {
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:9)
        """.trimIndent()
        )
    }

    fun `_test polyadic string literal diff`() {
        checkAcceptDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("exp" + "ect" + "ed", "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("actual", "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }
}