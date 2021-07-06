// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.parameterInfo.*
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.types.KotlinType

@Suppress("UnstableApiUsage")
enum class HintType(private val showDesc: String, defaultEnabled: Boolean) {

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
                    return provideTypeHint(namedFunc, paramList.endOffset)
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
                    return provideTypeHint(param, ident.endOffset)
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
            if (elem !is KtExpression) return emptyList()
            return provideLambdaReturnValueHints(elem)
        }
    },

    LAMBDA_IMPLICIT_PARAMETER_RECEIVER(
        KotlinBundle.message("hints.settings.lambda.receivers.parameters"),
        true
    ) {
        override fun isApplicable(elem: PsiElement) = elem is KtFunctionLiteral

        override fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
            ((elem as? KtFunctionLiteral)?.parent as? KtLambdaExpression)?.let {
                return provideLambdaImplicitHints(it)
            }
            return emptyList()
        }
    },

    SUSPENDING_CALL(
        KotlinBundle.message("hints.settings.suspending"),
        false
    ) {
        override fun isApplicable(elem: PsiElement) = elem.isNameReferenceInCall() && ApplicationManager.getApplication().isInternal

        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val callExpression = elem.parent as? KtCallExpression ?: return emptyList()
            return provideSuspendingCallHint(callExpression)?.let { listOf(it) } ?: emptyList()
        }
    };

    companion object {
        fun resolve(elem: PsiElement): HintType? {
            val applicableTypes = values().filter { it.isApplicable(elem) }
            return applicableTypes.firstOrNull()
        }

        fun resolveToEnabled(elem: PsiElement?): HintType? {

            val resolved = elem?.let { resolve(it) } ?: return null
            return if (resolved.enabled) {
                resolved
            } else {
                null
            }
        }
    }

    abstract fun isApplicable(elem: PsiElement): Boolean
    open fun provideHints(elem: PsiElement): List<InlayInfo> = emptyList()
    open fun provideHintDetails(elem: PsiElement): List<InlayInfoDetails> {
        return provideHints(elem).map(::InlayInfoDetails)
    }

    open class InlayInfoDetails(open val inlayInfo: InlayInfo)

    data class PsiInlayInfoDetails(override val inlayInfo: InlayInfo, val element: PsiElement): InlayInfoDetails(inlayInfo)

    data class TypedInlayInfoDetails(override val inlayInfo: InlayInfo, val type: KotlinType): InlayInfoDetails(inlayInfo)

    val option = Option("SHOW_${this.name}", this.showDesc, defaultEnabled)
    val enabled
        get() = option.get()
}