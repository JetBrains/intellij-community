// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.*
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStartOffsetIn

internal object KotlinPostfixTemplatePsiInfo : PostfixTemplatePsiInfo() {
    override fun createExpression(context: PsiElement, prefix: String, suffix: String): KtExpression {
        return KtPsiFactory(context.project).createExpression(prefix + context.text + suffix)
    }

    override fun getNegatedExpression(element: PsiElement): PsiElement {
        val targetElement = getTargetExpression(element) ?: return element
        return negateExpression(targetElement, KtPsiFactory(element.project))
    }

    private fun getTargetExpression(element: PsiElement): KtExpression? {
        return when (element) {
            is KtOperationReferenceExpression -> null
            is KtExpression -> element
            else -> null
        }
    }

    @RequiresReadLock
    @OptIn(KtAllowAnalysisOnEdt::class)
    private fun negateExpression(element: KtElement, factory: KtPsiFactory): PsiElement {
        fun replaceChild(parent: PsiElement, old: PsiElement, newText: String): KtExpression {
            val parentText = parent.text
            val childOffset = old.getStartOffsetIn(parent)
            val textBefore = parentText.substring(0, childOffset)
            val textAfter = parentText.substring(childOffset + old.textLength)
            return factory.createExpression(textBefore + newText + textAfter)
        }

        fun replaceChild(parent: PsiElement, old: PsiElement, new: PsiElement): KtExpression {
            return replaceChild(parent, old, new.text)
        }

        if (element is KtPrefixExpression && element.operationToken == KtTokens.EXCL) {
            val baseExpressionText = element.baseExpression
            if (baseExpressionText != null) {
                return factory.createExpression(baseExpressionText.text)
            }
        } else if (element is KtLabeledExpression) {
            val baseExpression = element.baseExpression
            if (baseExpression != null) {
                return replaceChild(element, baseExpression, negateExpression(baseExpression, factory))
            }
        } else if (element is KtAnnotatedExpression) {
            val baseExpression = element.baseExpression
            if (baseExpression != null) {
                return replaceChild(element, baseExpression, negateExpression(baseExpression, factory))
            }
        } else if (element is KtIsExpression) {
            if (!element.isNegated) {
                return replaceChild(element, element.operationReference, KtTokens.NOT_IS.value)
            } else {
                return replaceChild(element, element.operationReference, KtTokens.IS_KEYWORD.value)
            }
        } else if (element is KtBinaryExpression) {
            val mappedToken = BINARY_TOKEN_MAPPINGS[element.operationToken]
            if (mappedToken != null) {
                return replaceChild(element, element.operationReference, mappedToken.value)
            }
        } else if (element is KtConstantExpression) {
            if (KtPsiUtil.isTrueConstant(element)) {
                return factory.createExpression(KtTokens.FALSE_KEYWORD.value)
            } else if (KtPsiUtil.isFalseConstant(element)) {
                return factory.createExpression(KtTokens.TRUE_KEYWORD.value)
            }
        } else if (element is KtCallExpression) {
            val calleeExpression = element.calleeExpression
            if (calleeExpression is KtNameReferenceExpression) {
                allowAnalysisOnEdt {
                    analyze(element) {
                        val call = element.resolveCall().singleCallOrNull<KtCall>()
                        if (call is KtSimpleFunctionCall) {
                            val functionSymbol = call.partiallyAppliedSymbol.symbol
                            val callableId = functionSymbol.callableIdIfNonLocal
                            if (callableId != null && callableId.callableName in MAPPED_CALLABLE_NAMES) {
                                for (overriddenSymbol in functionSymbol.getAllOverriddenSymbols()) {
                                    val mappedCallableId = CALLABLE_MAPPINGS[overriddenSymbol.callableIdIfNonLocal]
                                    if (mappedCallableId != null) {
                                        return replaceChild(element, calleeExpression, mappedCallableId.callableName.asString())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (element is KtIfExpression) {
            val thenBranch = element.then
            val elseBranch = element.`else`
            if (thenBranch != null && elseBranch != null) {
                val newElement = element.copy() as KtIfExpression
                newElement.then?.replace(elseBranch.copy())
                newElement.`else`?.replace(thenBranch.copy())
                return factory.createExpression(newElement.text)
            }
        }

        if (shouldWrapOnNegation(element)) {
            return factory.createExpression("!(" + element.text + ")")
        }

        return factory.createExpression("!" + element.text)
    }

    private fun shouldWrapOnNegation(element: KtElement): Boolean {
        return when (element) {
            is KtNameReferenceExpression,
                is KtParenthesizedExpression,
                is KtBlockExpression,
                is KtObjectLiteralExpression,
                is KtLambdaExpression,
                is KtCallExpression,
                is KtConstantExpression,
                is KtStringTemplateExpression -> false
            else -> true
        }
    }

    private val BINARY_TOKEN_MAPPINGS: Map<KtSingleValueToken, KtSingleValueToken> = buildMap {
        fun register(positive: KtSingleValueToken, negative: KtSingleValueToken) {
            put(positive, negative)
            put(negative, positive)
        }

        register(KtTokens.IN_KEYWORD, KtTokens.NOT_IN)
        register(KtTokens.EQEQ, KtTokens.EXCLEQ)
        register(KtTokens.EQEQEQ, KtTokens.EXCLEQEQEQ)
        register(KtTokens.LT, KtTokens.GTEQ)
        register(KtTokens.LTEQ, KtTokens.GT)
    }

    private val CALLABLE_MAPPINGS: Map<CallableId, CallableId> = buildMap {
        fun register(positiveRaw: String, negativeRaw: String, registerReversed: Boolean = true) {
            val positive = parseCallableId(positiveRaw)
            val negative = parseCallableId(negativeRaw)
            put(positive, negative)
            if (registerReversed) {
                put(negative, positive)
            }
        }

        register("kotlin.collections#all", "kotlin.collections#none")
        register("kotlin.collections/Collection#isEmpty", "kotlin.collections#isNotEmpty")
        register("kotlin.collections#isEmpty", "kotlin.collections#isNotEmpty", registerReversed = false)
    }

    private val MAPPED_CALLABLE_NAMES: Set<Name> = CALLABLE_MAPPINGS.entries
        .flatMapTo(HashSet()) { listOf(it.key.callableName, it.value.callableName) }
}

private fun parseCallableId(raw: String): CallableId {
    val (classOrPackage, callableName) = raw.split('#').also { check(it.size == 2) }

    if ('/' in classOrPackage) {
        val classId = ClassId.fromString(classOrPackage)
        return CallableId(classId, Name.identifier(callableName))
    }

    return CallableId(FqName(classOrPackage), null, Name.identifier(callableName))
}