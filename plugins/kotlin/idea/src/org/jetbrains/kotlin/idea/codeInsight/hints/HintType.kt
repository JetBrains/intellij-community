// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.parameterInfo.*
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("UnstableApiUsage")
enum class HintType(@Nls private val showDesc: String, defaultEnabled: Boolean) {

    PROPERTY_HINT(
        KotlinBundle.message("hints.settings.types.property"),
        false
    ) {
        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            return providePropertyTypeHint(elem)
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is KtProperty && elem.getReturnTypeReference() == null && !elem.isLocal
    },

    LOCAL_VARIABLE_HINT(
        KotlinBundle.message("hints.settings.types.variable"),
        false
    ) {
        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            return providePropertyTypeHint(elem)
        }

        override fun isApplicable(elem: PsiElement): Boolean =
            (elem is KtProperty && elem.getReturnTypeReference() == null && elem.isLocal) ||
                    (elem is KtParameter && elem.isLoopParameter && elem.typeReference == null) ||
                    (elem is KtDestructuringDeclarationEntry && elem.getReturnTypeReference() == null)
    },

    FUNCTION_HINT(
        KotlinBundle.message("hints.settings.types.return"),
        false
    ) {
        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            (elem as? KtNamedFunction)?.let { namedFunc ->
                namedFunc.valueParameterList?.let { paramList ->
                    provideTypeHint(namedFunc, paramList.endOffset)?.let { return listOf(it) }
                }
            }
            return emptyList()
        }

        override fun isApplicable(elem: PsiElement): Boolean =
            elem is KtNamedFunction && !(elem.hasBlockBody() || elem.hasDeclaredReturnType())
    },

    PARAMETER_TYPE_HINT(
        KotlinBundle.message("hints.settings.types.parameter"),
        false
    ) {
        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            (elem as? KtParameter)?.let { param ->
                param.nameIdentifier?.let { ident ->
                    provideTypeHint(param, ident.endOffset)?.let { return listOf(it) }
                }
            }
            return emptyList()
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is KtParameter && elem.typeReference == null && !elem.isLoopParameter
    },

    PARAMETER_HINT(
        KotlinBundle.message("hints.title.argument.name.enabled"),
        true
    ) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val callElement = elem.getStrictParentOfType<KtCallElement>() ?: return emptyList()
            return provideArgumentNameHints(callElement)
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is KtValueArgumentList
    },

    LAMBDA_RETURN_EXPRESSION(
        KotlinBundle.message("hints.settings.lambda.return"),
        true
    ) {
        override fun isApplicable(elem: PsiElement) =
            elem is KtExpression && elem !is KtFunctionLiteral && !elem.isNameReferenceInCall()

        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            elem.safeAs<KtExpression>()?.let { expression ->
                provideLambdaReturnValueHints(expression)?.let { return listOf(it) }
            }
            return emptyList()
        }
    },

    LAMBDA_IMPLICIT_PARAMETER_RECEIVER(
        KotlinBundle.message("hints.settings.lambda.receivers.parameters"),
        true
    ) {
        override fun isApplicable(elem: PsiElement) = elem is KtFunctionLiteral

        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            elem.safeAs<KtFunctionLiteral>()?.parent.safeAs<KtLambdaExpression>()?.let { expression ->
                provideLambdaImplicitHints(expression)?.let { return it }
            }
            return emptyList()
        }
    },

    SUSPENDING_CALL(
        KotlinBundle.message("hints.settings.suspending"),
        false
    ) {
        override fun isApplicable(elem: PsiElement) = elem.isNameReferenceInCall() && isApplicationInternalMode()

        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val callExpression = elem.parent as? KtCallExpression ?: return emptyList()
            return provideSuspendingCallHint(callExpression)?.let { listOf(it) } ?: emptyList()
        }
    },

    RANGES(
        KotlinBundle.message("hints.settings.ranges"),
        true
    ) {
        override fun isApplicable(elem: PsiElement): Boolean = elem is KtBinaryExpression && elem.isRangeExpression()

        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            val binaryExpression = elem.safeAs<KtBinaryExpression>() ?: return emptyList()
            val leftExp = binaryExpression.left ?: return emptyList()
            val rightExp = binaryExpression.right ?: return emptyList()

            val resolvedCall = binaryExpression.operationReference.resolveToCall()
            val operation = resolvedCall?.candidateDescriptor?.fqNameSafe?.asString() ?: return emptyList()
            val (leftText, rightText) = when (operation) {
                "kotlin.ranges.downTo" -> {
                    KotlinBundle.message("hints.ranges.downTo.left") to KotlinBundle.message("hints.ranges.downTo.right")
                }
                "kotlin.ranges.until" -> {
                    KotlinBundle.message("hints.ranges.until.left") to KotlinBundle.message("hints.ranges.until.right")
                }
                else -> {
                    if (operation in rangeToTypes) KotlinBundle.message("hints.ranges.rangeTo.left") to KotlinBundle.message("hints.ranges.rangeTo.right") else return emptyList()
                }
            }
            val leftInfo = InlayInfo(text = leftText, offset = leftExp.endOffset)
            val rightInfo = InlayInfo(text = rightText, offset = rightExp.startOffset)
            return listOf(
                InlayInfoDetails(leftInfo, listOf(TextInlayInfoDetail(leftText, smallText = false))),
                InlayInfoDetails(rightInfo, listOf(TextInlayInfoDetail(rightText, smallText = false)))
            )
        }
    };

    companion object {
        fun resolve(elem: PsiElement): List<HintType> =
            values().filter { it.isApplicable(elem) }

        fun resolveToEnabled(elem: PsiElement?): HintType? =
            elem?.let { resolve(it) }?.firstOrNull { it.enabled }
    }

    abstract fun isApplicable(elem: PsiElement): Boolean
    open fun provideHints(elem: PsiElement): List<InlayInfo> = emptyList()
    open fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
        return provideHints(elem).map { InlayInfoDetails(it, listOf(TextInlayInfoDetail(it.text))) }
    }

    val option = Option("SHOW_${this.name}", { this.showDesc }, defaultEnabled)
    val enabled
        get() = option.get()
}

data class InlayInfoDetails(val inlayInfo: InlayInfo, val details: List<InlayInfoDetail>)

sealed class InlayInfoDetail(val text: String)

class TextInlayInfoDetail(text: String, val smallText: Boolean = true): InlayInfoDetail(text)
class TypeInlayInfoDetail(text: String, val fqName: String?): InlayInfoDetail(text)
class PsiInlayInfoDetail(text: String, val element: PsiElement): InlayInfoDetail(text)

private val rangeToTypes = setOf(
    "kotlin.Byte.rangeTo",
    "kotlin.Short.rangeTo",
    "kotlin.Char.rangeTo",
    "kotlin.Int.rangeTo",
    "kotlin.Long.rangeTo",
    "kotlin.UInt.rangeTo",
    "kotlin.ULong.rangeTo"
)
