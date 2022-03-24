// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.EmptyIntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.IdeaTestUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand

/**
 * Test suite for org.jetbrains.kotlin.idea.inspections.ConvertArgumentToSetInspection inspection class
 *
 * This inspection detects specific cases of function invocation patterns with several exceptions,
 * so the set of code samples becomes large. This test suite generates test cases on the fly.
 *
 * The inspection tests correspond to the common case. Individual tricky cases are tested
 * as a part of the test suite for the corresponding intention class which shares the implementation
 * with the inspection.
 *
 * @see org.jetbrains.kotlin.idea.intentions.IntentionTestGenerated.ConvertArgumentToSet
 */
class ConvertToSetInspectionTest15 : KotlinLightCodeInsightFixtureTestCase() {

    fun `test regular minus`() = runTests(
        receiverTypes = listOf("Iterable<Int>", "Set<Int>", "Sequence<Int>"),
        functionNames = listOf("minus"),
        argumentTypes = listOf("Iterable<Int>", "Sequence<Int>", "Array<Int>", "ArrayList<Int>"),
        generator = ::generateFunctionCall
    )

    fun `test regular minus no conversion`() = runTests(
        receiverTypes = listOf("Iterable<Int>", "Set<Int>", "Sequence<Int>"),
        functionNames = listOf("minus"),
        argumentTypes = listOf("Set<Int>", "HashSet<Int>, MutableSet<Int>"),
        generator = { parameters -> generateFunctionCall(parameters, applyConversion = false) }
    )

    fun `test infix minus`() = runTests(
        receiverTypes = listOf(
            "Iterable<Int>", "Set<Int>", "Sequence<Int>",
            "Map<Int, String>", "MutableMap<Int, String>"
        ),
        functionNames = listOf("-"),
        argumentTypes = listOf("Iterable<Int>", "List<Int>", "Sequence<Int>", "Array<Int>", "ArrayList<Int>"),
        generator = ::generateInfixFunctionCall
    )

    fun `test infix minus no conversion`() = runTests(
        receiverTypes = listOf(
            "Iterable<Int>", "Set<Int>", "Sequence<Int>",
            "Map<Int, String>", "MutableMap<Int, String>"
        ),
        functionNames = listOf("-"),
        argumentTypes = listOf("Set<Int>", "HashSet<Int>, MutableSet<Int>"),
        generator = { parameters -> generateInfixFunctionCall(parameters, applyConversion = false) }
    )

    fun `test intersect and subtract for generic types`() = runTests(
        receiverTypes = listOf("Iterable<Int>", "Set<Int>", "Array<Int>", "ArrayList<Int>"),
        functionNames = listOf("intersect"),
        argumentTypes = listOf("Iterable<Int>", "Sequence<Int>", "Array<Int>", "List<Int>", "ArrayList<Int>", "java.util.ArrayList<Int>"),
        generator = ::generateFunctionCall
    )

    fun `test intersect and subtract for generic types no conversion`() = runTests(
        receiverTypes = listOf("Iterable<Int>", "Set<Int>", "Array<Int>", "ArrayList<Int>"),
        functionNames = listOf("intersect", "subtract"),
        argumentTypes = listOf("Set<Int>", "HashSet<Int>, MutableSet<Int>", "LinkedList<Int>"),
        generator = { parameters -> generateFunctionCall(parameters, applyConversion = false) }
    )

    fun `test intersect and subtract for primitive arrays`() {
        val primitiveTypes = listOf("Byte", "Short", "Int", "Long", "Float", "Double", "Boolean", "Char")
        val functions = listOf("intersect", "subtract")
        for (primitiveType in primitiveTypes) {
            for (function in functions) {
                doTest(
                    generateFunctionCall(
                        CodeSampleParameters(
                            "${primitiveType}Array",
                            function,
                            "Iterable<${primitiveType}>"
                        )
                    )
                )
            }
        }
    }

    fun `test intersect and subtract for primitive arrays no conversion`() {
        val primitiveTypes = listOf("Byte", "Short", "Int", "Long", "Float", "Double", "Boolean", "Char")
        val functions = listOf("intersect", "subtract")
        for (primitiveType in primitiveTypes) {
            for (function in functions) {
                doTest(
                    generateFunctionCall(
                        CodeSampleParameters(
                            "${primitiveType}Array",
                            function,
                            "Set<${primitiveType}>"
                        ),
                        applyConversion = false
                    )
                )
            }
        }
    }

    fun `test MutableCollection removeAll and retainAll`() = runTests(
        receiverTypes = listOf("MutableCollection<Int>", "MutableSet<Int>", "HashSet<Int>"),
        functionNames = listOf("removeAll" , "retainAll"),
        argumentTypes = listOf("Iterable<Int>", "Sequence<Int>", "Array<Int>", "ArrayList<Int>"),
        generator = ::generateFunctionCall
    )

    fun `test MutableCollection removeAll and retainAll no conversion`() = runTests(
        receiverTypes = listOf("MutableCollection<Int>", "MutableSet<Int>", "HashSet<Int>"),
        functionNames = listOf("removeAll", "retainAll"),
        argumentTypes = listOf("Set<Int>", "HashSet<Int>, MutableSet<Int>"),
        generator = { parameters -> generateInfixFunctionCall(parameters, applyConversion = false) }
    )

    fun `test MutableCollection minusAssign`() {
        val receivers = listOf("MutableCollection<Int>", "MutableSet<Int>", "HashSet<Int>")
        val arguments = listOf("Iterable<Int>", "Sequence<Int>", "Array<Int>", "ArrayList<Int>")
        for (receiver in receivers) {
            for (argument in arguments) {
                doTest(
                    generateInfixFunctionCall(
                        CodeSampleParameters(
                            receiver,
                            "-=",
                            argument
                        )
                    )
                )
            }
        }
    }

    fun `test MutableCollection minusAssign no conversion`() {
        val receivers = listOf("MutableCollection<Int>", "MutableSet<Int>", "HashSet<Int>")
        val arguments = listOf("Set<Int>", "HashSet<Int>, MutableSet<Int>")
        for (receiver in receivers) {
            for (argument in arguments) {
                doTest(
                    generateInfixFunctionCall(
                        CodeSampleParameters(
                            receiver,
                            "-=",
                            argument
                        ),
                        applyConversion = false
                    )
                )
            }
        }
    }

    fun `test constant initializers no conversion`() {
        val initializers = listOf("arrayOf", "listOf", "setOf", "sequenceOf")
        for (function in initializers) {
            doTest(CodeSample(
                before = """
                    fun f(a: Iterable<Int>) {
                        a - <caret>${function}(1, 2, 3)
                    }
                """.trimIndent(),
                after = null,
                name = "Iterable<Int> - ${function}"
            ))
        }
    }

    fun `test empty initializers no conversion`() {
        val initializers = listOf("emptyArray", "emptyList", "emptySet", "emptySequence")
        for (function in initializers) {
            doTest(CodeSample(
                before = """
                    fun f(a: Iterable<Int>) {
                        a - <caret>${function}()
                    }
                """.trimIndent(),
                after = null,
                name = "Iterable<Int> - ${function}"
            ))
        }
    }

    fun `test mutable initializers`() {
        val initializers = listOf("arrayListOf", "mutableListOf")
        for (function in initializers) {
            doTest(CodeSample(
                before = """
                    fun f(a: Iterable<Int>) {
                        a - <caret>${function}(1, 2, 3)
                    }
                """.trimIndent(),
                after = """
                    fun f(a: Iterable<Int>) {
                        a - ${function}(1, 2, 3).toSet()
                    }
                """.trimIndent(),
                name = "Iterable<Int> - ${function}"
            ))
        }
    }

    /**
     * Test case to run the inspection
     *
     * @param before the code sample text with a &lt;caret&gt;
     * @param after  the code sample text after a quick fix has been applied
     * @param name   the test case name for diagnostic messages
     *
     * @property problemExpected a marker of expected quick fix application
     */
    private class CodeSample(
        val before: String,
        val after: String?,
        val name: String
    ) {
        val problemExpected = after != null
    }

    /**
     * Code sample generator parameters
     *
     * @param
     */
    private data class CodeSampleParameters(
        val receiverType: String,
        val functionName: String,
        val argumentType: String
    )

    /**
     * Batch test runner: generates and runs tests for different parameter combinations.
     *
     * The current implementation runs tests for all combinations of parameters,
     * but the function may switch to random sampling in the future if running all tests
     * is too slow.
     *
     * @param receiverTypes possible receiver types
     * @param functionNames possible function names
     * @param argumentTypes possible argument types
     */
    private fun runTests(
        receiverTypes: List<String>,
        functionNames: List<String>,
        argumentTypes: List<String>,
        generator: (CodeSampleParameters) -> CodeSample
    ) {
        // We generate the list of code samples instead of just running them in a loop
        // to provide a simple way to get a pseudo-random sample of them
        val examples = receiverTypes.flatMap { receiver ->
            functionNames.flatMap { function ->
                argumentTypes.map { argument ->
                    generator(CodeSampleParameters(receiver, function, argument))
                }
            }
        }

        examples.forEach { doTest(it) }
    }

    /**
     * Run an actual test on a code sample.
     *
     * @param sample a code sample to test
     */
    private fun doTest(sample: CodeSample) {
        val convertArgumentToSetInspection = ConvertArgumentToSetInspection()
        myFixture.enableInspections(convertArgumentToSetInspection)

        assertTrue("${sample.name}: <caret> is missing", sample.before.contains("<caret>"))

        val fileName = "foo.kt"
        val highlightDescription = KotlinBundle.message("can.convert.argument.to.set")

        withCustomCompilerOptions(sample.before, project, module) {
            ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, IdeaTestUtil.getMockJdk18())
            try {
                myFixture.configureByText(fileName, sample.before)
                val highlightInfos = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).filter {
                    it.description == highlightDescription
                }

                assertTrue(
                    if (!sample.problemExpected)
                        "${sample.name}: No problems should be detected at caret\n" +
                                "Detected problems: ${highlightInfos.joinToString { it.description }}"
                    else
                        "${sample.name}: Expected at least one problem at caret",
                    sample.problemExpected == highlightInfos.isNotEmpty()
                )

                if (sample.problemExpected && highlightInfos.isNotEmpty()) {
                    val localFixActions = highlightInfos
                        .flatMap { it.quickFixActionMarkers ?: emptyList() }
                        .map { it.first.action }
                        .filter { it !is EmptyIntentionAction }

                    TestCase.assertTrue(
                        "${sample.name}: No fix action found",
                        localFixActions.isNotEmpty()
                    )

                    val localFixAction = localFixActions.singleOrNull()
                    TestCase.assertTrue(
                        "${sample.name}: More than one fix action found",
                        localFixAction != null
                    )

                    if (localFixAction != null) {
                        project.executeWriteCommand(localFixAction.text, null) {
                            localFixAction.invoke(project, editor, file)
                        }

                        if (sample.after != null) {
                            myFixture.checkResult(sample.after)
                        }
                    }
                }
            } finally {
                myFixture.disableInspections(convertArgumentToSetInspection)
            }
        }
    }

    // Code sample generators

    private fun generateFunctionCall(
        parameters: CodeSampleParameters,
        applyConversion: Boolean = true
    ): CodeSample {
        return CodeSample(
            """
                fun f(a: ${parameters.receiverType}, b: ${parameters.argumentType}) {
                    a.${parameters.functionName}(<caret>b)
                }
            """.trimIndent(),
            if (applyConversion)
                """
                    fun f(a: ${parameters.receiverType}, b: ${parameters.argumentType}) {
                        a.${parameters.functionName}(b.toSet())
                    }
                """.trimIndent()
            else null,
            "${parameters.receiverType}.${parameters.functionName}(${parameters.argumentType})"
        )
    }

    private fun generateInfixFunctionCall(
        parameters: CodeSampleParameters,
        applyConversion: Boolean = true
    ): CodeSample {
        return CodeSample(
            """
                fun f(a: ${parameters.receiverType}, b: ${parameters.argumentType}) {
                    a ${parameters.functionName} <caret>b
                }
            """.trimIndent(),
            if (applyConversion)
                """
                    fun f(a: ${parameters.receiverType}, b: ${parameters.argumentType}) {
                        a ${parameters.functionName} b.toSet()
                    }
                """.trimIndent()
            else null,
            "${parameters.receiverType}.${parameters.functionName}(${parameters.argumentType})"
        )
    }
}