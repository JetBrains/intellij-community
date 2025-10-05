// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.jdk2k

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.expandedSymbol
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression

interface Transformation {
    operator fun invoke(callExpression: KtCallExpression, replacement: Replacement)
    fun isApplicableByPsi(callExpression: KtCallExpression): Boolean = true

    context(_: KaSession)
    fun isApplicableByAnalyze(callExpression: KtCallExpression): Boolean = true
}

object WithoutAdditionalTransformation : Transformation {
    override fun invoke(callExpression: KtCallExpression, replacement: Replacement) {
        val psiFactory = KtPsiFactory(callExpression.project)
        val calleeLength = callExpression.calleeExpression?.textLength ?: return
        val replaced = callExpression.getQualifiedExpressionForSelectorOrThis().replaced(
            psiFactory.createExpression("${replacement.kotlinFunctionFqName}${callExpression.text.substring(calleeLength)}")
        )
        shortenReferences(replaced)
    }
}

object ToKotlinPrint : Transformation {
    override fun invoke(callExpression: KtCallExpression, replacement: Replacement) {
        WithoutAdditionalTransformation.invoke(callExpression, replacement)
    }

    context(_: KaSession) override fun isApplicableByAnalyze(callExpression: KtCallExpression): Boolean =
        (callExpression.calleeExpression as? KtSimpleNameExpression)?.getReceiverExpression()?.resolveToCall()
            ?.singleVariableAccessCall()?.partiallyAppliedSymbol?.symbol?.callableId?.asSingleFqName() == FqName("java.lang.System.out")
}

object ToExtensionFunctionWithNonNullableReceiver : Transformation {
    override fun invoke(callExpression: KtCallExpression, replacement: Replacement) {
        val file = callExpression.containingKtFile
        val psiFactory = KtPsiFactory(callExpression.project)
        val valueArguments = callExpression.valueArguments
        val typeArguments = callExpression.typeArgumentList?.text ?: ""
        val receiverText = valueArguments.first().let { it.getArgumentExpression()?.text ?: it.text }
        val argumentsText = valueArguments.drop(1).joinToString(separator = ", ") { it.text }

        val oldExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
        val commentSaver = CommentSaver(oldExpression)

        val replaced = oldExpression.replaced(
            psiFactory.createExpression("($receiverText).${replacement.kotlinFunctionShortName}$typeArguments($argumentsText)")
        ) as KtDotQualifiedExpression
        val receiver = replaced.receiverExpression as KtParenthesizedExpression
        if (KtPsiUtil.areParenthesesUseless(receiver)) {
            receiver.expression?.let { receiver.replace(it) }
        }
        commentSaver.restore(replaced)

        if (replacement.shouldAddImport) {
            file.addImport(FqName(replacement.kotlinFunctionFqName))
        }
    }

    override fun isApplicableByPsi(callExpression: KtCallExpression): Boolean =
        callExpression.valueArguments.isNotEmpty()

    context(_: KaSession)
    override fun isApplicableByAnalyze(callExpression: KtCallExpression): Boolean =
        callExpression
            .valueArguments.firstOrNull()
            ?.getArgumentExpression()
            ?.expressionType
            ?.isMarkedNullable == false
}

object ToExtensionFunctionWithNullableReceiver : Transformation {
    override fun invoke(callExpression: KtCallExpression, replacement: Replacement) =
        ToExtensionFunctionWithNonNullableReceiver(callExpression, replacement)

    override fun isApplicableByPsi(callExpression: KtCallExpression): Boolean =
        ToExtensionFunctionWithNonNullableReceiver.isApplicableByPsi(callExpression)
}

object ToExtensionFunctionWithNullableReceiverForMutableCollection : Transformation {
    override fun invoke(callExpression: KtCallExpression, replacement: Replacement) =
        ToExtensionFunctionWithNonNullableReceiver(callExpression, replacement)

    override fun isApplicableByPsi(callExpression: KtCallExpression): Boolean =
        ToExtensionFunctionWithNonNullableReceiver.isApplicableByPsi(callExpression)

    context(_: KaSession) override fun isApplicableByAnalyze(callExpression: KtCallExpression): Boolean =
        callExpression.valueArguments.firstOrNull()?.getArgumentExpression()?.expressionType?.isMutableListOrSubtype() == true

    context(_: KaSession)
    private fun KaType?.isMutableList(): Boolean =
        this?.expandedSymbol?.classId?.asSingleFqName() == StandardNames.FqNames.mutableList

    context(_: KaSession)
    private fun KaType?.isMutableListOrSubtype(): Boolean =
        isMutableList() || this?.expandedSymbol?.superTypes?.reversed()?.any { it.isMutableList() } == true
}