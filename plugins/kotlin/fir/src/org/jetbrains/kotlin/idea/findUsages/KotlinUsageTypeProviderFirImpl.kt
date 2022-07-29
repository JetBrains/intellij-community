// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyzeWithReadAction
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.findUsages.UsageTypeEnum.*
import org.jetbrains.kotlin.idea.references.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.util.OperatorNameConventions

class KotlinUsageTypeProviderFirImpl : KotlinUsageTypeProvider() {

    override fun getUsageTypeEnumByReference(refExpr: KtReferenceExpression): UsageTypeEnum? {

        val reference = refExpr.mainReference
        check(reference is KtSimpleReference<*>) { "Reference should be KtSimpleReference but not ${reference::class}" }

        fun KtAnalysisSession.getFunctionUsageType(functionSymbol: KtFunctionLikeSymbol): UsageTypeEnum? {
            when (reference) {
                is KtArrayAccessReference ->
                    return when ((functionSymbol as KtFunctionSymbol).name) {
                        OperatorNameConventions.GET -> IMPLICIT_GET
                        OperatorNameConventions.SET -> IMPLICIT_SET
                        else -> error("Expected get or set operator but resolved to unexpected symbol {functionSymbol.render()}")
                    }
                is KtInvokeFunctionReference -> return IMPLICIT_INVOKE
                is KtConstructorDelegationReference -> return null
            }

            return when {
                refExpr.getParentOfTypeAndBranch<KtSuperTypeListEntry> { typeReference } != null -> SUPER_TYPE

                functionSymbol is KtConstructorSymbol && refExpr.getParentOfTypeAndBranch<KtAnnotationEntry> { typeReference } != null -> ANNOTATION

                with(refExpr.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression }) {
                    this?.calleeExpression is KtSimpleNameExpression
                } -> if (functionSymbol is KtConstructorSymbol) CLASS_NEW_OPERATOR else FUNCTION_CALL

                refExpr.getParentOfTypeAndBranch<KtBinaryExpression> { operationReference } != null || refExpr.getParentOfTypeAndBranch<KtUnaryExpression> { operationReference } != null || refExpr.getParentOfTypeAndBranch<KtWhenConditionInRange> { operationReference } != null -> FUNCTION_CALL

                else -> null
            }
        }

        return analyzeWithReadAction(refExpr) {
            when (val targetElement = reference.resolveToSymbol()) {
                is KtClassifierSymbol ->
                    when (targetElement) {
                        is KtEnumEntrySymbol -> getVariableUsageType(refExpr)
                        is KtClassOrObjectSymbol -> when {
                            targetElement.classKind == KtClassKind.COMPANION_OBJECT -> COMPANION_OBJECT_ACCESS
                            targetElement.classKind == KtClassKind.OBJECT -> getVariableUsageType(refExpr)
                            else -> getClassUsageType(refExpr)
                        }
                        else -> getClassUsageType(refExpr)
                    }
                is KtPackageSymbol -> //TODO FIR Implement package symbol type
                    if (targetElement is PsiPackage) getPackageUsageType(refExpr) else getClassUsageType(refExpr)
                is KtVariableLikeSymbol -> getVariableUsageType(refExpr)
                is KtFunctionLikeSymbol -> getFunctionUsageType(targetElement)
                else -> null
            }
        }
    }
}
