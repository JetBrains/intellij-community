// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ReplaceExplicitInvokeWithImplicitTest27 : KotlinLightCodeInsightFixtureTestCase() {
    fun test() {
        val argumentsInReceiver = listOf("", "<>{}", "<>()", "()", "[]")
        val argumentsInInvoke = listOf("{}", "()", "<>()", "<>{}", "<>(){}")
        for (firstArgumentInReceiver in argumentsInReceiver) {
            for (secondArgumentInReceiver in argumentsInReceiver) {
                if (secondArgumentInReceiver.isEmpty() && firstArgumentInReceiver.isNotEmpty()) continue
                for (invokeArgument in argumentsInInvoke) {
                    generatePrefixAndPostfixAndRun(
                        argumentsInReceiver = firstArgumentInReceiver + secondArgumentInReceiver,
                        invokeArguments = invokeArgument,
                    )
                }
            }
        }
    }

    private fun generatePrefixAndPostfixAndRun(argumentsInReceiver: String, invokeArguments: String) {
        val variants = listOf("", "a", "a()", "a[]", "a{}")
        val prefixes = variants.map { if (it.isNotEmpty()) "$it." else it }
        val postfixes = variants.map { if (it.isNotEmpty()) ".$it" else it }
        for (firstPrefix in prefixes) {
            for (secondPrefix in prefixes) {
                if (secondPrefix.isEmpty() && firstPrefix.isNotEmpty()) continue

                for (firstPostfix in postfixes) {
                    for (secondPostfix in postfixes) {
                        if (secondPostfix.isEmpty() && firstPostfix.isNotEmpty()) continue

                        doTest(
                            prefixBeforeReceiver = firstPrefix + secondPrefix,
                            argumentsInReceiver = argumentsInReceiver,
                            invokeArguments = invokeArguments,
                            postfixAfterInvoke = firstPostfix + secondPostfix,
                        )
                    }
                }
            }
        }
    }

    private fun doTest(prefixBeforeReceiver: String, argumentsInReceiver: String, invokeArguments: String, postfixAfterInvoke: String) {
        val before = "${prefixBeforeReceiver}receiver${argumentsInReceiver}.invoke${invokeArguments}$postfixAfterInvoke"
        val after = if (
            argumentsInReceiver.lastOrNull()?.let { it == ')' || it == '}' } == true &&
            invokeArguments.firstOrNull()?.let { it == '{' } == true
        )
            "(${prefixBeforeReceiver}receiver${argumentsInReceiver})${invokeArguments}$postfixAfterInvoke"
        else
            "${prefixBeforeReceiver}receiver${argumentsInReceiver}${invokeArguments}$postfixAfterInvoke"

        val factory = KtPsiFactory(project)
        val expressionBefore = factory.createExpression(before)
        val expressionAfter = factory.createExpression(after)
        val qualifiedExpression = expressionBefore.descendantsOfType<KtDotQualifiedExpression>().firstOrNull {
            it.selectorExpression?.safeAs<KtCallExpression>()?.calleeExpression?.text == "invoke"
        } ?: error("Invoke call not found")

        val expressionAfterTransformation = OperatorToFunctionIntention.replaceExplicitInvokeCallWithImplicit(qualifiedExpression)
            ?: error("impossible convert explicit to implicit")

        val actualExpressionToCheck = expressionAfterTransformation.parentsWithSelf.takeWhile { it !is KtProperty }.last()
        assertEquals(
            "Text for: '${expressionAfter.text}' ('$before' -> '$after')",
            DebugUtil.psiTreeToString(expressionAfter, false),
            DebugUtil.psiTreeToString(actualExpressionToCheck, false),
        )
    }
}