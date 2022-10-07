// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.calls.calls
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
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsight.utils.getClassId
import org.jetbrains.kotlin.idea.codeinsight.utils.isAnnotatedDeep
import org.jetbrains.kotlin.idea.codeinsight.utils.isSetterParameter
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class RemoveExplicitTypeIntention :
    AbstractKotlinApplicatorBasedIntention<KtDeclaration, KotlinApplicatorInput.Empty>(KtDeclaration::class),
    HighPriorityAction {

    override fun getApplicator(): KotlinApplicator<KtDeclaration, KotlinApplicatorInput.Empty> = applicator {
        familyAndActionName(KotlinBundle.lazyMessage("remove.explicit.type.specification"))
        isApplicableByPsi { declaration ->
            when {
                declaration.containingFile is KtCodeFragment -> false
                declaration.typeReference == null || declaration.typeReference?.isAnnotatedDeep() == true -> false
                declaration is KtParameter -> declaration.isLoopParameter || declaration.isSetterParameter
                declaration is KtNamedFunction -> true
                declaration is KtProperty || declaration is KtPropertyAccessor -> declaration.getInitializerOrGetterInitializer() != null
                else -> false
            }
        }
        applyTo { declaration, _ -> declaration.removeTypeReference() }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclaration> =
        applicabilityRanges ranges@{ declaration ->
            val typeReference = declaration.typeReference ?: return@ranges emptyList()

            if (declaration is KtParameter && declaration.isSetterParameter)
                return@ranges listOf(typeReference.textRange.shiftLeft(declaration.startOffset))

            val typeReferenceRelativeEndOffset = typeReference.endOffset - declaration.startOffset
            listOf(TextRange(0, typeReferenceRelativeEndOffset))
        }

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtDeclaration, KotlinApplicatorInput.Empty> =
        inputProvider { declaration ->
            when {
                declaration is KtParameter -> KotlinApplicatorInput.Empty

                declaration is KtNamedFunction && declaration.hasBlockBody() ->
                    if (declaration.getReturnKtType().isUnit) KotlinApplicatorInput.Empty else null

                declaration is KtCallableDeclaration && publicReturnTypeShouldBePresentInApiMode(declaration) -> null
                explicitTypeMightBeNeededForCorrectTypeInference(declaration) -> null
                else -> KotlinApplicatorInput.Empty
            }
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
        get() = ((this as? KtProperty) ?: (this as? KtPropertyAccessor)?.property)?.isVar ?: false

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

        val typeCanBeRemoved = if (declaration.isVar) initializerType.isEqualTo(explicitType) else initializerType.isSubTypeOf(explicitType)
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
                evaluatedConstant?.let { initializer.getKtType() }
            }
        }
    }

    private fun KtAnalysisSession.returnTypeOfCallDependsOnTypeParameters(callExpression: KtCallExpression): Boolean {
        val call = callExpression.resolveCall().calls.singleOrNull() as? KtFunctionCall<*> ?: return true
        val callSymbol = call.partiallyAppliedSymbol.symbol
        val typeParameters = callSymbol.typeParameters
        val returnType = callSymbol.returnType
        return typeParameters.any { typeReferencesTypeParameter(it, returnType) }
    }

    private fun KtAnalysisSession.typeReferencesTypeParameter(typeParameter: KtTypeParameterSymbol, type: KtType): Boolean {
        return when (type) {
            is KtTypeParameterType -> type.symbol == typeParameter
            is KtNonErrorClassType -> type.typeArguments.mapNotNull { it.type }.any { typeReferencesTypeParameter(typeParameter, it) }
            else -> false
        }
    }
}