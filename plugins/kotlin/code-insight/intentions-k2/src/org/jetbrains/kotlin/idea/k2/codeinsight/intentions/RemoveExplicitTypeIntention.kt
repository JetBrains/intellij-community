// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.components.DefaultTypeClassIds
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.idea.codeinsight.utils.getClassId
import org.jetbrains.kotlin.idea.codeinsight.utils.isAnnotatedDeep
import org.jetbrains.kotlin.idea.codeinsight.utils.isSetterParameter
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveExplicitTypeIntention : AbstractKotlinApplicableIntention<KtDeclaration>(KtDeclaration::class) {
    override fun getFamilyName(): String = KotlinBundle.message("remove.explicit.type.specification")
    override fun getActionName(element: KtDeclaration): String = familyName

    override fun isApplicableByPsi(element: KtDeclaration): Boolean = when {
        element.typeReference == null || element.typeReference?.isAnnotatedDeep() == true -> false
        element is KtParameter -> element.isLoopParameter || element.isSetterParameter
        element is KtNamedFunction -> true
        element is KtProperty || element is KtPropertyAccessor -> element.getInitializerOrGetterInitializer() != null
        else -> false
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtDeclaration): Boolean = when {
        element is KtParameter -> true
        element is KtNamedFunction && element.hasBlockBody() -> element.getReturnKtType().isUnit
        element is KtCallableDeclaration && publicReturnTypeShouldBePresentInApiMode(element) -> false
        else -> !explicitTypeMightBeNeededForCorrectTypeInference(element)
    }

    override fun apply(element: KtDeclaration, project: Project, editor: Editor?) {
        element.removeTypeReference()
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclaration> =
        applicabilityRanges ranges@{ declaration ->
            val typeReference = declaration.typeReference ?: return@ranges emptyList()

            if (declaration is KtParameter && declaration.isSetterParameter) {
                return@ranges listOf(typeReference.textRangeIn(declaration))
            }

            val typeReferenceRelativeEndOffset = typeReference.endOffset - declaration.startOffset
            listOf(TextRange(0, typeReferenceRelativeEndOffset))
        }

    private val KtDeclaration.typeReference: KtTypeReference?
        get() = when (this) {
            is KtCallableDeclaration -> typeReference
            is KtPropertyAccessor -> returnTypeReference
            else -> null
        }

    private fun KtDeclaration.removeTypeReference() {
        if (this is KtCallableDeclaration) {
            typeReference = null
        } else if (this is KtPropertyAccessor) {
            val first = rightParenthesis?.nextSibling ?: return
            val last = returnTypeReference ?: return
            deleteChildRange(first, last)
        }
    }

    private fun KtDeclaration.getInitializerOrGetterInitializer(): KtExpression? {
        if (this is KtDeclarationWithInitializer && initializer != null) return initializer
        return (this as? KtProperty)?.getter?.initializer
    }

    private val KtDeclaration.isVar: Boolean
        get() {
            val property = (this as? KtProperty) ?: (this as? KtPropertyAccessor)?.property
            return property?.isVar == true
        }

    private fun KtAnalysisSession.publicReturnTypeShouldBePresentInApiMode(declaration: KtCallableDeclaration): Boolean {
        if (declaration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) == ExplicitApiMode.DISABLED) return false

        if (declaration.containingClassOrObject?.isLocal == true) return false
        if (declaration is KtFunction && declaration.isLocal) return false
        if (declaration is KtProperty && declaration.isLocal) return false

        val symbolWithVisibility = declaration.getSymbol() as? KtSymbolWithVisibility ?: return false
        return isPublicApi(symbolWithVisibility)
    }

    private fun KtAnalysisSession.explicitTypeMightBeNeededForCorrectTypeInference(declaration: KtDeclaration): Boolean {
        val initializer = declaration.getInitializerOrGetterInitializer() ?: return true
        val initializerType = getInitializerTypeIfContextIndependent(initializer) ?: return true
        val explicitType = declaration.getReturnKtType()

        val typeCanBeRemoved = if (declaration.isVar) {
            initializerType.isEqualTo(explicitType)
        } else {
            initializerType.isSubTypeOf(explicitType)
        }
        return !typeCanBeRemoved
    }

    private fun KtAnalysisSession.getInitializerTypeIfContextIndependent(initializer: KtExpression): KtType? {
        return when (initializer) {
            is KtStringTemplateExpression -> buildClassType(DefaultTypeClassIds.STRING)
            is KtConstantExpression -> initializer.getClassId()?.let { buildClassType(it) }
            is KtCallExpression -> {
                val isNotContextFree = initializer.typeArgumentList == null && returnTypeOfCallDependsOnTypeParameters(initializer)
                if (isNotContextFree) null else initializer.getKtType()
            }

            else -> {
                // get type for expressions that a compiler views as constants, e.g. `1 + 2`
                val evaluatedConstant = initializer.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION)
                if (evaluatedConstant != null) initializer.getKtType() else null
            }
        }
    }

    private fun KtAnalysisSession.returnTypeOfCallDependsOnTypeParameters(callExpression: KtCallExpression): Boolean {
        val call = callExpression.resolveCall().singleFunctionCallOrNull() ?: return true
        val callSymbol = call.partiallyAppliedSymbol.symbol
        val typeParameters = callSymbol.typeParameters
        val returnType = callSymbol.returnType
        return typeParameters.any { typeReferencesTypeParameter(it, returnType) }
    }

    private fun KtAnalysisSession.typeReferencesTypeParameter(typeParameter: KtTypeParameterSymbol, type: KtType): Boolean {
        return when (type) {
            is KtTypeParameterType -> type.symbol == typeParameter
            is KtNonErrorClassType -> type.ownTypeArguments.mapNotNull { it.type }.any { typeReferencesTypeParameter(typeParameter, it) }
            else -> false
        }
    }
}