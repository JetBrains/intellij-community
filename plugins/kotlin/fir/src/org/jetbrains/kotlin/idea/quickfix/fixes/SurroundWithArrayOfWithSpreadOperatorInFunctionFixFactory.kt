/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.quickfix.fixes

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtUsualClassType
import org.jetbrains.kotlin.builtins.StandardNames.FqNames.arrayClassFqNameToPrimitiveType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicatorInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinApplicatorTargetWithInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticModCommandFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.withInput
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.modCommandApplicator
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.resolve.ArrayFqNames

object SurroundWithArrayOfWithSpreadOperatorInFunctionFixFactory {

    class Input(val fullyQualifiedArrayOfCall: String, val shortArrayOfCall: String) : KotlinApplicatorInput

    private val applicator = modCommandApplicator<KtExpression, Input> {
        familyName(KotlinBundle.lazyMessage("surround.with.array.of"))
        actionName { _, input ->
            KotlinBundle.getMessage("surround.with.0", input.shortArrayOfCall)
        }
        applyTo { psi, input ->
            val argument = psi.getParentOfType<KtValueArgument>(false) ?: return@applyTo
            val argumentName = argument.getArgumentName()?.asName ?: return@applyTo
            val argumentExpression = argument.getArgumentExpression() ?: return@applyTo

            val psiFactory = KtPsiFactory(psi.project)

            val surroundedWithArrayOf = psiFactory.createExpressionByPattern("${input.fullyQualifiedArrayOfCall}($0)", argumentExpression)
            val newArgument = psiFactory.createArgument(surroundedWithArrayOf, argumentName)

            val replacedArgument = argument.replace(newArgument) as KtValueArgument
            val qualifiedCallExpression = replacedArgument.getArgumentExpression() as KtDotQualifiedExpression

            // We want to properly shorten the fully-qualified `kotlin.arrayOf(...)` call.
            // To shorten only this call and avoid shortening the arguments, we pass only the selector part (`arrayOf`) to the shortener.
            qualifiedCallExpression.getQualifiedElementSelector()?.let { shortenReferences(it) }
        }
    }

    val assigningSingleElementToVarargInNamedFormFunction =
        diagnosticModCommandFixFactory(KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionError::class, applicator) { diagnostic ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }
    val assigningSingleElementToVarargInNamedFormFunctionWarning =
        diagnosticModCommandFixFactory(KtFirDiagnostic.AssigningSingleElementToVarargInNamedFormFunctionWarning::class, applicator) { diagnostic ->
            createFix(diagnostic.expectedArrayType, diagnostic.psi)
        }

    context(KtAnalysisSession)
    @Suppress("unused")
    private fun createFix(expectedArrayType: KtType, psi: KtExpression): List<KotlinApplicatorTargetWithInput<KtExpression, Input>> {
        val arrayClassId = (expectedArrayType as? KtUsualClassType)?.classId
        val primitiveType = arrayClassFqNameToPrimitiveType[arrayClassId?.asSingleFqName()?.toUnsafe()]
        val arrayOfCallName = ArrayFqNames.PRIMITIVE_TYPE_TO_ARRAY[primitiveType] ?: ArrayFqNames.ARRAY_OF_FUNCTION
        return listOf(psi withInput Input("kotlin." + arrayOfCallName.identifier, arrayOfCallName.identifier))
    }
}