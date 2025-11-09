// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.KotlinArrayToStringInspection.Context
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

private val IMPLICIT_TO_STRING_METHOD_NAMES: Set<String> = setOf("append", "print", "println")
private val TO_STRING_CALLABLE_ID = CallableId(StandardClassIds.Any, Name.identifier("toString"))

// This also handles Kotlin typealiases to Java underlying types
private val IMPLICIT_TO_STRING_CALLABLE_IDS: Set<CallableId> = setOf(
    CallableId(ClassId.topLevel(FqName("java.lang.StringBuilder")), Name.identifier("append")),
    CallableId(ClassId.topLevel(FqName("java.lang.StringBuffer")), Name.identifier("append")),
    CallableId(ClassId.topLevel(FqName("java.io.PrintStream")), Name.identifier("print")),
    CallableId(ClassId.topLevel(FqName("java.io.PrintStream")), Name.identifier("println")),
    CallableId(ClassId.topLevel(FqName("java.io.PrintWriter")), Name.identifier("print")),
    CallableId(ClassId.topLevel(FqName("java.io.PrintWriter")), Name.identifier("println")),
    CallableId(FqName("kotlin.io"), Name.identifier("print")),
    CallableId(FqName("kotlin.io"), Name.identifier("println"))
)

internal class KotlinArrayToStringInspection : KotlinApplicableInspectionBase<KtExpression, Context>() {
    data class Context(val isNestedArray: Boolean, val isImplicitConversion: Boolean)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }

        override fun visitStringTemplateEntry(entry: KtStringTemplateEntry) {
            if (entry is KtStringTemplateEntryWithExpression) {
                entry.expression?.let { visitTargetElement(it, holder, isOnTheFly) }
            }
        }
    }

    override fun isApplicableByPsi(element: KtExpression): Boolean {
        return when (element) {
            is KtQualifiedExpression -> {
                val callExpression = element.selectorExpression as? KtCallExpression ?: return false
                if (callExpression.valueArguments.isNotEmpty()) return false
                val calleeName = callExpression.calleeExpression?.text ?: return false
                calleeName == "toString"
            }

            is KtBinaryExpression -> {
                element.operationReference.text == "+"
            }

            is KtCallExpression -> {
                val calleeName = element.getCallNameExpression()?.text ?: return false
                calleeName in IMPLICIT_TO_STRING_METHOD_NAMES
            }

            else -> {
                // Check if this is inside a string template
                val parent = element.parent
                parent is KtBlockStringTemplateEntry || parent is KtSimpleNameStringTemplateEntry
            }
        }
    }

    override fun KaSession.prepareContext(element: KtExpression): Context? {
        return when (element) {
            is KtQualifiedExpression -> {
                val receiverType = element.receiverExpression.expressionType ?: return null
                if (!receiverType.isArrayOrPrimitiveArray) return null
                val callExpression = element.selectorExpression as? KtCallExpression ?: return null
                val call = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
                val functionSymbol = call.symbol as? KaNamedFunctionSymbol ?: return null
                if (functionSymbol.callableId != TO_STRING_CALLABLE_ID) return null

                Context(receiverType.isNestedArray, isImplicitConversion = false)
            }

            is KtBinaryExpression -> {
                // String concatenation only works as String + Any (not Any + String)
                if (element.left?.expressionType?.isStringType != true) return null
                val rightType = element.right?.expressionType ?: return null
                if (!rightType.isArrayOrPrimitiveArray) return null

                Context(rightType.isNestedArray, isImplicitConversion = true)
            }

            is KtCallExpression -> {
                val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
                val functionSymbol = call.symbol as? KaNamedFunctionSymbol ?: return null
                val callableId = functionSymbol.callableId ?: return null
                if (callableId !in IMPLICIT_TO_STRING_CALLABLE_IDS) return null

                val arguments = element.valueArguments
                if (arguments.size != 1) return null
                val argType = arguments[0].getArgumentExpression()?.expressionType ?: return null
                if (!argType.isArrayOrPrimitiveArray) return null

                Context(argType.isNestedArray, isImplicitConversion = true)
            }

            else -> {
                val parent = element.parent
                if (parent !is KtBlockStringTemplateEntry && parent !is KtSimpleNameStringTemplateEntry) return null
                val expressionType = element.expressionType ?: return null
                if (!expressionType.isArrayOrPrimitiveArray) return null

                Context(expressionType.isNestedArray, isImplicitConversion = true)
            }
        }
    }

    override fun getApplicableRanges(element: KtExpression): List<TextRange> {
        return when (element) {
            is KtQualifiedExpression -> {
                val selectorExpression = element.selectorExpression ?: return emptyList()
                ApplicabilityRange.single(element) { selectorExpression }
            }

            is KtBinaryExpression -> {
                ApplicabilityRange.single(element) { element.operationReference }
            }

            is KtCallExpression -> {
                val calleeExpression = element.getCallNameExpression() ?: return emptyList()
                ApplicabilityRange.single(element) { calleeExpression }
            }

            else -> {
                // For string templates, highlight the entire expression
                ApplicabilityRange.single(element) { element }
            }
        }
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor {
        val message = if (context.isImplicitConversion) {
            KotlinBundle.message("array.implicit.to.string.problem.descriptor")
        } else {
            KotlinBundle.message("array.to.string.problem.descriptor")
        }

        val fixes = if (context.isNestedArray) {
            arrayOf(ReplaceWithContentToStringFix(), ReplaceWithContentDeepToStringFix())
        } else {
            arrayOf(ReplaceWithContentToStringFix())
        }

        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ message,
            /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            /* onTheFly = */ onTheFly,
            /* ...fixes = */ *fixes
        )
    }
}

private class ReplaceWithContentToStringFix : KotlinModCommandQuickFix<KtExpression>() {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.content.to.string")

    override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
        replaceWithArrayContentMethod(project, element, methodName = "contentToString")
    }
}

private class ReplaceWithContentDeepToStringFix : KotlinModCommandQuickFix<KtExpression>(), HighPriorityAction {
    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("replace.with.content.deep.to.string")

    override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
        replaceWithArrayContentMethod(project, element, methodName = "contentDeepToString")
    }
}

private fun replaceWithArrayContentMethod(project: Project, element: KtExpression, methodName: String) {
    val psiFactory = KtPsiFactory(project)
    when (element) {
        is KtQualifiedExpression -> {
            val receiver = element.receiverExpression
            val operator = element.operationSign.value // . or ?.
            val newExpression = psiFactory.createExpressionByPattern("$0$operator$methodName()", receiver)
            element.replace(newExpression)
        }

        is KtBinaryExpression -> {
            val left = element.left ?: return
            val right = element.right ?: return
            // Only string + array pattern is valid for string concatenation
            val newExpression = psiFactory.createExpressionByPattern("$0 + $1.$methodName()", left, right)
            element.replace(newExpression)
        }

        is KtCallExpression -> {
            val calleeName = element.getCallNameExpression()?.text ?: return
            when (calleeName) {
                "append" -> {
                    // receiver.append(array) -> receiver.append(array.contentToString())
                    val receiver = element.parent as? KtDotQualifiedExpression
                    if (receiver != null) {
                        val receiverExpr = receiver.receiverExpression
                        val argument = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
                        val operator = receiver.operationSign.value
                        val newExpression = psiFactory.createExpressionByPattern(
                            "$0${operator}$calleeName($1.$methodName())",
                            receiverExpr,
                            argument
                        )
                        receiver.replace(newExpression)
                    } else {
                        // Just append(array) without receiver -> append(array.contentToString())
                        val argument = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
                        val newExpression = psiFactory.createExpressionByPattern("$calleeName($0.$methodName())", argument)
                        element.replace(newExpression)
                    }
                }

                "print", "println" -> {
                    // print(array) -> print(array.contentToString())
                    val argument = element.valueArguments.firstOrNull()?.getArgumentExpression() ?: return
                    val newExpression = psiFactory.createExpressionByPattern("$calleeName($0.$methodName())", argument)
                    element.replace(newExpression)
                }
            }
        }

        else -> {
            // String template
            val parent = element.parent
            when (parent) {
                is KtBlockStringTemplateEntry -> {
                    // "${array}" -> "${array.contentToString()}"
                    val newExpression = psiFactory.createExpressionByPattern("$0.$methodName()", element)
                    element.replace(newExpression)
                }
                is KtSimpleNameStringTemplateEntry -> {
                    // "$array" -> "${array.contentToString()}"
                    val newExpression = psiFactory.createExpressionByPattern("$0.$methodName()", element)
                    val newEntry = psiFactory.createBlockStringTemplateEntry(newExpression)
                    parent.replace(newEntry)
                }
            }
        }
    }
}
