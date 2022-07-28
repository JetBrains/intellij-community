// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.codeInspection.util.IntentionName
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
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

enum class HintType(
    @Nls private val description: String,
    @Nls @IntentionName val showDescription: String,
    @Nls @IntentionName val hideDescription: String,
    defaultEnabled: Boolean
) {

    PROPERTY_HINT(
        KotlinBundle.message("hints.settings.types.property"),
        KotlinBundle.message("hints.settings.show.types.property"),
        KotlinBundle.message("hints.settings.dont.show.types.property"),
        false
    ) {
        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            return providePropertyTypeHint(e)
        }

        override fun isApplicable(e: PsiElement): Boolean = e is KtProperty && e.getReturnTypeReference() == null && !e.isLocal
    },

    LOCAL_VARIABLE_HINT(
        KotlinBundle.message("hints.settings.types.variable"),
        KotlinBundle.message("hints.settings.show.types.variable"),
        KotlinBundle.message("hints.settings.dont.show.types.variable"),
        false
    ) {
        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            return providePropertyTypeHint(e)
        }

        override fun isApplicable(e: PsiElement): Boolean =
            (e is KtProperty && e.getReturnTypeReference() == null && e.isLocal) ||
                    (e is KtParameter && e.isLoopParameter && e.typeReference == null) ||
                    (e is KtDestructuringDeclarationEntry && e.getReturnTypeReference() == null)
    },

    FUNCTION_HINT(
        KotlinBundle.message("hints.settings.types.return"),
        KotlinBundle.message("hints.settings.show.types.return"),
        KotlinBundle.message("hints.settings.dont.show.types.return"),
        false
    ) {
        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            (e as? KtNamedFunction)?.let { namedFunc ->
                namedFunc.valueParameterList?.let { paramList ->
                    provideTypeHint(namedFunc, paramList.endOffset)?.let { return listOf(it) }
                }
            }
            return emptyList()
        }

        override fun isApplicable(e: PsiElement): Boolean =
            e is KtNamedFunction && !(e.hasBlockBody() || e.hasDeclaredReturnType())
    },

    PARAMETER_TYPE_HINT(
        KotlinBundle.message("hints.settings.types.parameter"),
        KotlinBundle.message("hints.settings.show.types.parameter"),
        KotlinBundle.message("hints.settings.dont.show.types.parameter"),
        false
    ) {
        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            (e as? KtParameter)?.let { param ->
                param.nameIdentifier?.let { ident ->
                    provideTypeHint(param, ident.endOffset)?.let { return listOf(it) }
                }
            }
            return emptyList()
        }

        override fun isApplicable(e: PsiElement): Boolean = e is KtParameter && e.typeReference == null && !e.isLoopParameter
    },

    PARAMETER_HINT(
        KotlinBundle.message("hints.title.argument.name.enabled"),
        KotlinBundle.message("hints.title.show.argument.name.enabled"),
        KotlinBundle.message("hints.title.dont.show.argument.name.enabled"),
        true
    ) {
        override fun provideHints(e: PsiElement): List<InlayInfo> {
            val callElement = e.getStrictParentOfType<KtCallElement>() ?: return emptyList()
            return provideArgumentNameHints(callElement)
        }

        override fun isApplicable(e: PsiElement): Boolean = e is KtValueArgumentList
    },

    LAMBDA_RETURN_EXPRESSION(
        KotlinBundle.message("hints.settings.lambda.return"),
        KotlinBundle.message("hints.settings.show.lambda.return"),
        KotlinBundle.message("hints.settings.dont.show.lambda.return"),
        true
    ) {
        override fun isApplicable(e: PsiElement): Boolean {
            return e is KtExpression &&
                    e !is KtFunctionLiteral &&
                    !e.isNameReferenceInCall() &&
                    e.isLambdaReturnValueHintsApplicable()
        }

        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            e.safeAs<KtExpression>()?.let { expression ->
                provideLambdaReturnValueHints(expression)?.let { return listOf(it) }
            }
            return emptyList()
        }
    },

    LAMBDA_IMPLICIT_PARAMETER_RECEIVER(
        KotlinBundle.message("hints.settings.lambda.receivers.parameters"),
        KotlinBundle.message("hints.settings.show.lambda.receivers.parameters"),
        KotlinBundle.message("hints.settings.dont.show.lambda.receivers.parameters"),
        true
    ) {
        override fun isApplicable(e: PsiElement): Boolean {
            return e is KtFunctionLiteral && e.parent is KtLambdaExpression && (e.parent as KtLambdaExpression).leftCurlyBrace.isFollowedByNewLine()
        }

        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            e.safeAs<KtFunctionLiteral>()?.parent.safeAs<KtLambdaExpression>()?.let { expression ->
                provideLambdaImplicitHints(expression)?.let { return it }
            }
            return emptyList()
        }
    },

    SUSPENDING_CALL(
        KotlinBundle.message("hints.settings.suspending"),
        KotlinBundle.message("hints.settings.show.suspending"),
        KotlinBundle.message("hints.settings.dont.show.suspending"),
        false
    ) {
        override fun isApplicable(e: PsiElement) = e.isNameReferenceInCall() && isApplicationInternalMode()

        override fun provideHints(e: PsiElement): List<InlayInfo> {
            val callExpression = e.parent as? KtCallExpression ?: return emptyList()
            return provideSuspendingCallHint(callExpression)?.let { listOf(it) } ?: emptyList()
        }
    },

    RANGES(
        KotlinBundle.message("hints.settings.ranges"),
        KotlinBundle.message("hints.settings.show.ranges"),
        KotlinBundle.message("hints.settings.dont.show.ranges"),
        true
    ) {
        override fun isApplicable(e: PsiElement): Boolean = e is KtBinaryExpression && e.isRangeExpression()

        override fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> {
            val binaryExpression = e.safeAs<KtBinaryExpression>() ?: return emptyList()
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
                in rangeToTypes -> {
                    KotlinBundle.message("hints.ranges.rangeTo.left") to KotlinBundle.message("hints.ranges.rangeTo.right")
                }
                "kotlin.ranges.rangeUntil" -> {
                    KotlinBundle.message("hints.ranges.rangeUntil.left") to KotlinBundle.message("hints.ranges.rangeUntil.right")
                }
                else -> return emptyList()
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
        private val values = values()

        fun resolve(e: PsiElement): List<HintType> =
            values.filter { it.isApplicable(e) }

    }

    abstract fun isApplicable(e: PsiElement): Boolean
    open fun provideHints(e: PsiElement): List<InlayInfo> = emptyList()
    open fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> =
        provideHints(e).map { InlayInfoDetails(it, listOf(TextInlayInfoDetail(it.text))) }

    val option = Option("SHOW_${this.name}", { this.description }, defaultEnabled)
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
    "kotlin.ULong.rangeTo",
    "kotlin.ranges.rangeTo",
)
