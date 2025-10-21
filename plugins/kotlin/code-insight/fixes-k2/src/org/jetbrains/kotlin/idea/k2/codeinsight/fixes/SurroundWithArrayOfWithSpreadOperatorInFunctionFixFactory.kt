// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaUsualClassType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames.arrayClassFqNameToPrimitiveType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.resolve.ArrayFqNames

internal object SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory {

    private data class ElementContext(
        val fullyQualifiedArrayOfCall: String,
        val shortArrayOfCall: String,
    )

    private class SurroundWithArrayModCommandAction(
        element: KtExpression,
        elementContext: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtExpression, ElementContext>(element, elementContext) {

        override fun getFamilyName(): String =
            KotlinBundle.message("surround.with.array.of")

        override fun getPresentation(
            context: ActionContext,
            element: KtExpression,
        ): Presentation {
            val (_, shortArrayOfCall) = getElementContext(context, element)
            return Presentation.of(KotlinBundle.message("surround.with.0", shortArrayOfCall))
        }

        override fun invoke(
            actionContext: ActionContext,
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
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionError ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }

    val assigningSingleElementToVarargInNamedFormFunctionWarning =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionWarning ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }

    private fun createFix(
        expectedArrayType: KaType,
        element: KtExpression,
    ): List<SurroundWithArrayModCommandAction> {
        val arrayClassId = (expectedArrayType as? KaUsualClassType)?.classId
        val primitiveType = arrayClassFqNameToPrimitiveType[arrayClassId?.asSingleFqName()?.toUnsafe()]
        val arrayOfCallName = ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY[primitiveType] ?: ArrayFqNames.ARRAY_OF_FUNCTION

        val elementContext = ElementContext(
            fullyQualifiedArrayOfCall = "kotlin." + arrayOfCallName.identifier,
            shortArrayOfCall = arrayOfCallName.identifier,
        )
        return listOf(
            SurroundWithArrayModCommandAction(element, elementContext),
        )
    }
}