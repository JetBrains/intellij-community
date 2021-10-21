/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames.arrayClassFqNameToPrimitiveType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.fir.api.fixes.HLApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.fir.api.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.fir.api.fixes.withInput
import org.jetbrains.kotlin.idea.util.shortenReferences
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.resolve.ArrayFqNames

object SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory {

    class Input(val fullyQualifiedArrayOfCall: String, val shortArrayOfCall: String) : HLApplicatorInput

    val applicator = applicator<KtExpression, Input> {
        familyName(KotlinBundle.getMessage("surround.with.array.of"))
        actionName { _, input ->
            KotlinBundle.getMessage("surround.with.0", input.shortArrayOfCall)
        }
        applyTo { psi, input ->
            val argument = psi.getParentOfType<KtValueArgument>(false) ?: return@applyTo
            val argumentName = argument.getArgumentName()?.asName ?: return@applyTo
            val argumentExpression = argument.getArgumentExpression() ?: return@applyTo

            val factory = KtPsiFactory(argumentExpression)

            val surroundedWithArrayOf = factory.createExpressionByPattern("${input.fullyQualifiedArrayOfCall}($0)", argumentExpression)
            val newArgument = factory.createArgument(surroundedWithArrayOf, argumentName)

            val replacedArgument = argument.replace(newArgument) as KtValueArgument
            // Essentially this qualifier is always `kotlin` in `kotlin.arrayOf(...)`. We choose to shorten this part so that the argument
            // is not touched by reference shortener.
            val arrayOfQualifier = (replacedArgument.getArgumentExpression() as KtDotQualifiedExpression).receiverExpression!!
            shortenReferences(arrayOfQualifier)
        }
    }

    val assigningSingleElementToVarargInNamedFormFunction =
        diagnosticFixFactory(KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionError::class, applicator) { diagnostic ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }
    val assigningSingleElementToVarargInNamedFormFunctionWarning =
        diagnosticFixFactory(KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionWarning::class, applicator) { diagnostic ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }

    private fun createFix(expectedArrayType: KtType, psi: KtExpression): List<HLApplicatorTargetWithInput<KtExpression, Input>> {
        val arrayClassId = (expectedArrayType as? KtUsualClassType)?.classId
        val primitiveType = arrayClassFqNameToPrimitiveType[arrayClassId?.asSingleFqName()?.toUnsafe()]
        val arrayOfCallName = ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY[primitiveType] ?: ArrayFqNames.ARRAY_OF_FUNCTION
        return listOf(psi withInput Input("kotlin." + arrayOfCallName.identifier, arrayOfCallName.identifier))
    }
}