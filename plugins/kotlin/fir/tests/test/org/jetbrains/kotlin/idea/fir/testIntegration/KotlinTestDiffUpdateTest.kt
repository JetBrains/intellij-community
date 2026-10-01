// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.testIntegration


import com.intellij.idea.TestFor
import com.intellij.openapi.editor.Document
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.java.testIntegration.JvmTestDiffUpdateTest
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts

@Suppress("NewClassNamingConvention", "SameParameterValue")
class KotlinTestDiffUpdateTest : JvmTestDiffUpdateTest() {
    override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
        super.tuneFixture(moduleBuilder)
        moduleBuilder.addLibrary("kotlin-stdlib", TestKotlinArtifacts.kotlinStdlib.toString())
    }

    private fun checkHasNoDiff(
        @Language("kotlin") before: String,
        testClass: String,
        testName: String,
        expected: String,
        actual: String,
        stackTrace: String
    ) = checkHasNoDiff(before, testClass, testName, expected, actual, stackTrace, fileExt)

    private fun checkAcceptFullDiff(
        @Language("kotlin") before: String,
        @Language("kotlin") after: String,
        testClass: String,
        testName: String,
        expected: String,
        actual: String,
        stackTrace: String,
        location: String? = null,
    ) = checkAcceptFullDiff(before, after, testClass, testName, expected, actual, stackTrace, fileExt, location)

    private fun checkPhysicalDiff(
        @Language("kotlin") before: String,
        @Language("kotlin") after: String,
        diffAfter: String,
        testClass: String,
        testName: String,
        expected: String,
        actual: String,
        stackTrace: String,
        change: (Document) -> Unit
    ) = checkPhysicalDiff(before, after, diffAfter, testClass, testName, expected, actual, stackTrace, fileExt, change)

    fun `test accept string literal diff`() {
        checkAcceptFullDiff(
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

    fun `test accept string literal diff with actual call`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test
                  
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected", getActual(getActual(getActual("actual"))))
                }
            
                private fun getActual(str: String): String {
                    return str
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
                  
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("actual", getActual(getActual(getActual("actual"))))
                }
            
                private fun getActual(str: String): String {
                    return str
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept diff is not available when expected is not a string literal`() {
        checkHasNoDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJunitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(true, "actual")
                }
            }
        """.trimIndent(), "MyJunitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.fail(Assert.java:89)
            at org.junit.Assert.failNotEquals(Assert.java:835)
            at org.junit.Assert.assertEquals(Assert.java:120)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJunitTest.testFoo(MyJunitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept diff is not available when actual is not a string literal`() {
        checkHasNoDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJunitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected", true)
                }
            }
        """.trimIndent(), "MyJunitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.fail(Assert.java:89)
            at org.junit.Assert.failNotEquals(Assert.java:835)
            at org.junit.Assert.assertEquals(Assert.java:120)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJunitTest.testFoo(MyJunitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test physical string literal change sync`() {
        checkPhysicalDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected<caret>", "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expectedFoo", "actual")
                }
            }
        """.trimIndent(), diffAfter = "expectedFoo", "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        ) { document -> document.insertString(myFixture.editor.caretModel.offset, "Foo") }
    }

    fun `test physical non-string literal change sync`() {
        checkPhysicalDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest<caret> {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected", "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTestFoo {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected", "actual")
                }
            }
        """.trimIndent(), diffAfter = "expected", "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        ) { document -> document.insertString(myFixture.editor.caretModel.offset, "Foo") }
    }

    fun `test accept string literal diff with escape`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected", "actual\"")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("actual\"", "actual\"")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual\"", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept parameter reference diff`() {
        checkAcceptFullDiff(
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

    fun `test accept parameter reference diff in named call`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    doTest(ex = "expected", other = 0)
                }
                
                private fun doTest(other: Int, ex: String) {
                    Assert.assertEquals(ex, "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                @Test
                fun testFoo() {
                    doTest(ex = "actual", other = 0)
                }
                
                private fun doTest(other: Int, ex: String) {
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

    fun `test accept parameter reference diff with multiple calls on same line`() {
        checkAcceptFullDiff(
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

    fun `test accept local variable reference diff`() {
        checkAcceptFullDiff(
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

    fun `test accept field reference diff`() {
        checkAcceptFullDiff(
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

    fun `test accept parameter reference diff found using value search`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                data class TestData(val expected: String)
            
                @Test
                fun testFoo() {
                    doTest("expected")
                }
            
                private fun doTest(expected: String) {
                    val testData = TestData(expected)
                    Assert.assertEquals(testData.expected, "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                data class TestData(val expected: String)
            
                @Test
                fun testFoo() {
                    doTest("actual")
                }
            
                private fun doTest(expected: String) {
                    val testData = TestData(expected)
                    Assert.assertEquals(testData.expected, "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.doTest(MyJUnitTest.kt:14)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:9)
        """.trimIndent()
        )
    }

    fun `test no diff parameter reference search found on duplicate expected literal`() {
        checkHasNoDiff("""
            import org.junit.Assert
            import org.junit.Test
            
            class MyJUnitTest {
                data class TestData(val expected: String)
            
                @Test
                fun testFoo() {
                    testBar("expected")
                }
            
                private fun testBar(expected: String) {
                    doTest(expected, "expected")
                }
            
                private fun doTest(expected: String, out: String) {
                    System.out.println(out)
                    val testData = TestData(expected)
                    Assert.assertEquals(testData.expected, "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.doTest(MyJUnitTest.kt:19)
            at MyJUnitTest.testBar(MyJUnitTest.kt:13)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:9)
        """.trimIndent())
    }

    fun `_test accept polyadic string literal diff`() {
        checkAcceptFullDiff(
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

    fun `test accept raw string literal diff without trimIndent`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}line one
            line two${TQ}, "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}actual one
            actual two${TQ}, "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept raw string literal diff through trimIndent`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one
                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        actual one
                        actual two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept raw string literal diff keeps blank lines and gains a line`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one

                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one

                        line two
                        line three
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\n\nline two", "line one\n\nline two\nline three", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    // A real stack trace continues below the test method into the test runner and the JDK.
    // These frames have no project source.
    fun `test accept raw string literal diff from an expression bodied test method`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    line one
                    line two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String) {
                    val text = fileContent.trimIndent()
                    assertText(text, "actual")
                }

                private fun assertText(expectedText: String, actual: String) {
                    Assert.assertEquals(expectedText, actual)
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    actual one
                    actual two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String) {
                    val text = fileContent.trimIndent()
                    assertText(text, "actual")
                }

                private fun assertText(expectedText: String, actual: String) {
                    Assert.assertEquals(expectedText, actual)
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.assertText(MyJUnitTest.kt:17)
            at MyJUnitTest.doTest(MyJUnitTest.kt:13)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:6)
            at java.base/java.lang.reflect.Method.invoke(Method.java:565)
            at com.intellij.testFramework.junit5.LogTestName.runTest(LogTestName.kt:52)
            at kotlinx.coroutines.scheduling.CoroutineScheduler${'$'}Worker.run(CoroutineScheduler.kt:877)
        """.trimIndent()
        )
    }

    // PyCodeInsightTestCase fails in this shape. The JUnit 5 assert takes the message last.
    fun `test accept raw string literal diff from a junit5 assert with a message`() {
        checkAcceptFullDiff(
            """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    line one
                    line two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String) {
                    val text = fileContent.trimIndent()
                    assertText(text)
                }

                private fun assertText(expectedText: String) {
                    Assertions.assertEquals(expectedText, "actual", "one assertion differs")
                }
            }
        """.trimIndent(), """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    actual one
                    actual two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String) {
                    val text = fileContent.trimIndent()
                    assertText(text)
                }

                private fun assertText(expectedText: String) {
                    Assertions.assertEquals(expectedText, "actual", "one assertion differs")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.jupiter.api.AssertionFailureBuilder.build(AssertionFailureBuilder.java:151)
            at org.junit.jupiter.api.AssertionFailureBuilder.buildAndThrow(AssertionFailureBuilder.java:132)
            at org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:197)
            at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:182)
            at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1156)
            at MyJUnitTest.assertText(MyJUnitTest.kt:17)
            at MyJUnitTest.doTest(MyJUnitTest.kt:13)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:6)
            at java.base/java.lang.reflect.Method.invoke(Method.java:565)
        """.trimIndent()
        )
    }

    // The full PyCodeInsightTestCase shape. The test has a backticked name in a `@Nested inner class`.
    // The expected value goes through a trimmed local, and one frame name has Kotlin's name mangling.
    // The inner class calls the private `test` through the synthetic `access$test`.
    // The frame of `access$test` has only the line of the class declaration.
    fun `test accept raw string literal diff from a nested inner class with a backticked name`() {
        checkAcceptFullDiff(
            """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Nested
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Nested
                inner class UnionInference {
                    @Test
                    fun `union iteration yields union of element types`() = test(${TQ}
                        line one
                        line two
                    ${TQ}.trimIndent())
                }

                private fun test(fileContent: String) {
                    val originalText = fileContent.trimIndent()
                    collectAndCheckHighlighting(originalText)
                }

                private fun collectAndCheckHighlighting(expectedText: String) {
                    val actualText = generateActualText(expectedText)
                    Assertions.assertEquals(expectedText, actualText, "one assertion differs")
                }

                private fun generateActualText(text: String): String = "actual"
            }
        """.trimIndent(), """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Nested
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Nested
                inner class UnionInference {
                    @Test
                    fun `union iteration yields union of element types`() = test(${TQ}
                        actual one
                        actual two
                    ${TQ}.trimIndent())
                }

                private fun test(fileContent: String) {
                    val originalText = fileContent.trimIndent()
                    collectAndCheckHighlighting(originalText)
                }

                private fun collectAndCheckHighlighting(expectedText: String) {
                    val actualText = generateActualText(expectedText)
                    Assertions.assertEquals(expectedText, actualText, "one assertion differs")
                }

                private fun generateActualText(text: String): String = "actual"
            }
        """.trimIndent(), "MyJUnitTest", "union iteration yields union of element types",
            "line one\nline two", "actual one\nactual two", """
            at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1156)
            at MyJUnitTest.collectAndCheckHighlighting-3nIYWDw(MyJUnitTest.kt:22)
            at MyJUnitTest.test(MyJUnitTest.kt:17)
            at MyJUnitTest.access${'$'}test(MyJUnitTest.kt:5)
            at MyJUnitTest${'$'}UnionInference.union iteration yields union of element types(MyJUnitTest.kt:9)
            at java.base/java.lang.reflect.Method.invoke(Method.java:565)
        """.trimIndent(),
            location = "java:test://MyJUnitTest${'$'}UnionInference/union iteration yields union of element types"
        )
    }

    // A call that omits a default argument goes through the synthetic `doTest$default`.
    // Its frame has only the line of the declaration, and the default value on that line is a call.
    fun `test accept raw string literal diff through a default argument bridge`() {
        checkAcceptFullDiff(
            """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    line one
                    line two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String, otherFiles: List<String> = emptyList()) {
                    val text = fileContent.trimIndent()
                    assertText(text)
                }

                private fun assertText(expectedText: String) {
                    Assertions.assertEquals(expectedText, "actual", "one assertion differs")
                }
            }
        """.trimIndent(), """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    actual one
                    actual two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String, otherFiles: List<String> = emptyList()) {
                    val text = fileContent.trimIndent()
                    assertText(text)
                }

                private fun assertText(expectedText: String) {
                    Assertions.assertEquals(expectedText, "actual", "one assertion differs")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1156)
            at MyJUnitTest.assertText(MyJUnitTest.kt:17)
            at MyJUnitTest.doTest(MyJUnitTest.kt:13)
            at MyJUnitTest.doTest${'$'}default(MyJUnitTest.kt:11)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:6)
            at java.base/java.lang.reflect.Method.invoke(Method.java:565)
        """.trimIndent()
        )
    }

    // Two arguments of the test call hold the expected text.
    // Only the parameter that the trimmed local carries on tells which literal to update.
    fun `test accept raw string literal diff tracks the parameter through a trimmed local`() {
        checkAcceptFullDiff(
            """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    line one
                    line two
                ${TQ}.trimIndent(), ${TQ}
                    line one
                    line two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String, unused: String) {
                    val text = fileContent.trimIndent()
                    assertText(text)
                }

                private fun assertText(expectedText: String) {
                    Assertions.assertEquals(expectedText, "actual", "one assertion differs")
                }
            }
        """.trimIndent(), """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = doTest(${TQ}
                    actual one
                    actual two
                ${TQ}.trimIndent(), ${TQ}
                    line one
                    line two
                ${TQ}.trimIndent())

                private fun doTest(fileContent: String, unused: String) {
                    val text = fileContent.trimIndent()
                    assertText(text)
                }

                private fun assertText(expectedText: String) {
                    Assertions.assertEquals(expectedText, "actual", "one assertion differs")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1156)
            at MyJUnitTest.assertText(MyJUnitTest.kt:20)
            at MyJUnitTest.doTest(MyJUnitTest.kt:16)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:6)
            at java.base/java.lang.reflect.Method.invoke(Method.java:565)
        """.trimIndent()
        )
    }

    fun `test accept raw string literal diff with a partial change`() {
        checkChangeDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one
                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one
                        line 2
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent(), fileExt
        ) { document ->
            val offset = document.text.indexOf("two")
            document.replaceString(offset, offset + "two".length, "2")
        }
    }

    // The literal keeps its empty blank line, and the new blank line holds whitespace.
    fun `test accept raw string literal diff with a whitespace only line`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one

                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one

                        line two
            ${" ".repeat(16)}
                        line three
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\n\nline two", "line one\n\nline two\n    \nline three", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    // No `trimIndent()` literal has a value with a common indent, so the literal does not change.
    fun `test accept raw string literal diff keeps the literal for a value that trimIndent cannot give`() {
        val source = """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one
                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent()
        checkAcceptFullDiff(source, source, "MyJUnitTest", "testFoo", "line one\nline two", "  actual one\n  actual two", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    // The diff shows the trimmed value of the literal after a change in the source file.
    fun `test physical raw string literal change sync through trimIndent`() {
        checkPhysicalDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one<caret>
                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line oneFoo
                        line two
                    ${TQ}.trimIndent(), "actual")
                }
            }
        """.trimIndent(), diffAfter = "line oneFoo\nline two", "MyJUnitTest", "testFoo", "line one\nline two", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        ) { document -> document.insertString(myFixture.editor.caretModel.offset, "Foo") }
    }

    fun `test accept raw string literal diff through helpers that invoke a lambda`() {
        checkAcceptFullDiff(
            """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = test($TQ
                    line one
                    line two
                $TQ.trimIndent())

                private fun test(fileContent: String) {
                    runTestBody {
                        doTest(fileContent)
                    }
                }

                private fun runTestBody(body: () -> Unit) {
                    body()
                }

                private fun doTest(fileContent: String) {
                    val originalText = fileContent.trimIndent()
                    withInspections {
                        collectAndCheckHighlighting(originalText)
                    }
                }

                private fun withInspections(body: () -> Unit) {
                    body()
                }

                private fun collectAndCheckHighlighting(expectedText: String) {
                    val actualText = generateActualText(expectedText)
                    Assertions.assertEquals(expectedText, actualText, "one assertion differs")
                }

                private fun generateActualText(text: String): String = "actual"
            }
        """.trimIndent(), """
            import org.junit.jupiter.api.Assertions
            import org.junit.jupiter.api.Test

            class MyJUnitTest {
                @Test
                fun testFoo() = test($TQ
                    actual one
                    actual two
                $TQ.trimIndent())

                private fun test(fileContent: String) {
                    runTestBody {
                        doTest(fileContent)
                    }
                }

                private fun runTestBody(body: () -> Unit) {
                    body()
                }

                private fun doTest(fileContent: String) {
                    val originalText = fileContent.trimIndent()
                    withInspections {
                        collectAndCheckHighlighting(originalText)
                    }
                }

                private fun withInspections(body: () -> Unit) {
                    body()
                }

                private fun collectAndCheckHighlighting(expectedText: String) {
                    val actualText = generateActualText(expectedText)
                    Assertions.assertEquals(expectedText, actualText, "one assertion differs")
                }

                private fun generateActualText(text: String): String = "actual"
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", $$"""
            at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1210)
            at MyJUnitTest.collectAndCheckHighlighting(MyJUnitTest.kt:34)
            at MyJUnitTest.doTest$lambda$0(MyJUnitTest.kt:24)
            at MyJUnitTest.withInspections(MyJUnitTest.kt:29)
            at MyJUnitTest.doTest(MyJUnitTest.kt:23)
            at MyJUnitTest.test$lambda$0(MyJUnitTest.kt:13)
            at MyJUnitTest.runTestBody(MyJUnitTest.kt:18)
            at MyJUnitTest.test(MyJUnitTest.kt:12)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:6)
            at java.base/java.lang.reflect.Method.invoke(Method.java:565)
        """.trimIndent()
        )
    }

    fun `test accept raw string literal diff through trimMargin`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        |line one
                        |line two
                    ${TQ}.trimMargin(), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        |actual one
                        |
                        |  actual two
                    ${TQ}.trimMargin(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\n\n  actual two", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept raw string literal diff through trimMargin with a custom prefix`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        >line one
                        >line two
                    ${TQ}.trimMargin(">"), "actual")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        >actual one
                        >actual two
                    ${TQ}.trimMargin(">"), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    fun `test accept diff is not available for trimMargin with a computed prefix`() {
        checkHasNoDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                private fun prefix(): String = "|"

                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        |line one
                        |line two
                    ${TQ}.trimMargin(prefix()), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:9)
        """.trimIndent()
        )
    }

    // The IDE cannot write an edit back to a literal that does not start with a line break.
    @TestFor(issues = ["KTIJ-24923"])
    fun `test accept diff is not available for a single line trimIndent literal`() {
        checkHasNoDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("expected".trimIndent(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    // The value text of a regular string literal holds escapes, not line breaks.
    @TestFor(issues = ["KTIJ-24923"])
    fun `test accept diff is not available for a trimIndent string literal with escapes`() {
        checkHasNoDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals("\n    line one\n    line two\n".trimIndent(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    // The literal closes on its last line, so it needs a blank line to keep a trailing line break.
    @TestFor(issues = ["KTIJ-24923"])
    fun `test accept raw string literal diff through trimIndent adds a trailing line break`() {
        checkAcceptFullDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        line one
                        line two${TQ}.trimIndent(), "actual one\nactual two\n")
                }
            }
        """.trimIndent(), """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}
                        actual one
                        actual two

                    ${TQ}.trimIndent(), "actual one\nactual two\n")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "line one\nline two", "actual one\nactual two\n", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    // A raw literal on one line has no line break either.
    @TestFor(issues = ["KTIJ-24923"])
    fun `test accept diff is not available for a single line trimMargin literal`() {
        checkHasNoDiff(
            """
            import org.junit.Assert
            import org.junit.Test

            class MyJUnitTest {
                @Test
                fun testFoo() {
                    Assert.assertEquals(${TQ}|expected${TQ}.trimMargin(), "actual")
                }
            }
        """.trimIndent(), "MyJUnitTest", "testFoo", "expected", "actual", """
            at org.junit.Assert.assertEquals(Assert.java:117)
            at org.junit.Assert.assertEquals(Assert.java:146)
            at MyJUnitTest.testFoo(MyJUnitTest.kt:7)
        """.trimIndent()
        )
    }

    companion object {
        private const val fileExt = "kt"

        /** Holds a triple quote, so that a fixture can contain a raw string literal. */
        private const val TQ = "\"\"\""
    }
}