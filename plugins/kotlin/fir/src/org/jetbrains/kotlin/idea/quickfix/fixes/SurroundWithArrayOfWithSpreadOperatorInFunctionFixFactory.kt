// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames.arrayClassFqNameToPrimitiveType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.resolve.ArrayFqNames

object SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory {

    private class SurroundWithArrayModCommandAction(
        element: KtExpression,
        elementContext: ElementContext,
    ) : KotlinModCommandAction<KtExpression, SurroundWithArrayModCommandAction.ElementContext>(element, elementContext) {

        data class ElementContext(
            val fullyQualifiedArrayOfCall: String,
            val shortArrayOfCall: String,
        ) : KotlinModCommandAction.ElementContext

        override fun getFamilyName(): String = KotlinBundle.message("surround.with.array.of")

        override fun getActionName(
            context: ActionContext,
            element: KtExpression,
            elementContext: ElementContext,
        ): String = KotlinBundle.getMessage("surround.with.0", elementContext.shortArrayOfCall)

        override fun invoke(
            context: ActionContext,
            element: KtExpression,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val argument = element.getParentOfType<KtValueArgument>(false) ?: return
            val argumentName = argument.getArgumentName()?.asName ?: return
            val argumentExpression = argument.getArgumentExpression() ?: return

            val psiFactory = KtPsiFactory(element.project)

            val surroundedWithArrayOf =
                psiFactory.createExpressionByPattern("${elementContext.fullyQualifiedArrayOfCall}($0)", argumentExpression)
            val newArgument = psiFactory.createArgument(surroundedWithArrayOf, argumentName)

            val replacedArgument = argument.replace(newArgument) as KtValueArgument
            val qualifiedCallExpression = replacedArgument.getArgumentExpression() as KtDotQualifiedExpression

            // We want to properly shorten the fully-qualified `kotlin.arrayOf(...)` call.
            // To shorten only this call and avoid shortening the arguments, we pass only the selector part (`arrayOf`) to the shortener.
            qualifiedCallExpression.getQualifiedElementSelector()?.let { shortenReferences(it) }
        }
    }

    val assigningSingleElementToVarargInNamedFormFunction =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionError ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }

    val assigningSingleElementToVarargInNamedFormFunctionWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionWarning ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }

    context(KtAnalysisSession)
    private fun createFix(
        expectedArrayType: KtType,
        element: KtExpression,
    ): List<SurroundWithArrayModCommandAction> {
        val arrayClassId = (expectedArrayType as? KtUsualClassType)?.classId
        val primitiveType = arrayClassFqNameToPrimitiveType[arrayClassId?.asSingleFqName()?.toUnsafe()]
        val arrayOfCallName = ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY[primitiveType] ?: ArrayFqNames.ARRAY_OF_FUNCTION

        val elementContext = SurroundWithArrayModCommandAction.ElementContext(
            fullyQualifiedArrayOfCall = "kotlin." + arrayOfCallName.identifier,
            shortArrayOfCall = arrayOfCallName.identifier,
        )
        return listOf(
            SurroundWithArrayModCommandAction(element, elementContext),
        )
    }
}