// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.components.KtConstantEvaluationMode
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.codeinsight.utils.TypeParameterUtils.returnTypeOfCallDependsOnTypeParameters
import org.jetbrains.kotlin.idea.codeinsight.utils.TypeParameterUtils.typeReferencesTypeParameter
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveExplicitTypeIntention :
    KotlinApplicableModCommandAction<KtDeclaration, Unit>(KtDeclaration::class) {

    override fun getApplicableRanges(element: KtDeclaration): List<TextRange> {
        val typeReference = element.typeReference
            ?: return emptyList()

        val textRange = if (element is KtParameter && element.isSetterParameter) {
            typeReference.textRangeIn(element)
        } else {
            val typeReferenceRelativeEndOffset = typeReference.endOffset - element.startOffset
            TextRange(0, typeReferenceRelativeEndOffset)
        }

        return listOf(textRange)
    }

    override fun isApplicableByPsi(element: KtDeclaration): Boolean {
        val typeReference = element.typeReference ?: return false

        return when {
            !isApplicableByTypeReference(element, typeReference) -> false
            element is KtParameter -> element.isLoopParameter || element.isSetterParameter
            element is KtNamedFunction -> true
            element is KtProperty || element is KtPropertyAccessor -> element.getInitializerOrGetterInitializer() != null
            else -> false
        }
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtDeclaration): Unit? = when {
        element is KtParameter -> true
        element is KtNamedFunction && element.hasBlockBody() -> element.getReturnKtType().isUnit
        element is KtCallableDeclaration && publicReturnTypeShouldBePresentInApiMode(element) -> false
        else -> !element.isExplicitTypeReferenceNeededForTypeInferenceByAnalyze()
    }.asUnit

    private val KtDeclaration.typeReference: KtTypeReference?
        get() = when (this) {
            is KtCallableDeclaration -> typeReference
            is KtPropertyAccessor -> returnTypeReference
            else -> null
        }

    private fun isApplicableByTypeReference(element: KtDeclaration, typeReference: KtTypeReference): Boolean =
        !typeReference.isAnnotatedDeep() && !element.isExplicitTypeReferenceNeededForTypeInferenceByPsi(typeReference)

    context(KtAnalysisSession)
    private fun publicReturnTypeShouldBePresentInApiMode(declaration: KtCallableDeclaration): Boolean {
        if (declaration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) == ExplicitApiMode.DISABLED) return false

        if (declaration.containingClassOrObject?.isLocal == true) return false
        if (declaration is KtFunction && declaration.isLocal) return false
        if (declaration is KtProperty && declaration.isLocal) return false

        val symbolWithVisibility = declaration.getSymbol() as? KtSymbolWithVisibility ?: return false
        return isPublicApi(symbolWithVisibility)
    }

    context(KtAnalysisSession)
    private fun KtDeclaration.isExplicitTypeReferenceNeededForTypeInferenceByAnalyze(): Boolean {
        val typeReference = typeReference ?: return false
        val initializer = getInitializerOrGetterInitializer() ?: return true
        val explicitType = getReturnKtType()

        // The initializer may require an explicit type, but an error type is definitely not it. The intention makes a conscious decision to
        // allow the user to quickly remove completely erroneous explicit types.
        //
        // The situation is more fuzzy with errors in type arguments. Here, it makes sense not to remove the type, but rather to fix it. And
        // with a little bit of type information, the intention has a better chance of deciding whether the initializer needs that explicit
        // type. So we don't check the `explicitType` for nested errors.
        if (explicitType is KtErrorType) return false

        if (!isInitializerTypeContextIndependent(initializer, typeReference)) return true

        val initializerType = initializer.getKtType() ?: return true
        val typeCanBeRemoved = if (isVar) {
            initializerType.isEqualTo(explicitType)
        } else {
            initializerType.isSubTypeOf(explicitType)
        }
        return !typeCanBeRemoved
    }

    /**
     * Currently we don't use on-air resolve in the implementation, therefore the function might return false negative results
     * for expressions that are not covered.
     */
    context(KtAnalysisSession)
    private fun isInitializerTypeContextIndependent(
        initializer: KtExpression,
        typeReference: KtTypeReference,
    ): Boolean = when (initializer) {
        is KtStringTemplateExpression -> true
        // `val n: Int = 1` - type of `1` is context-independent
        // `val n: Long = 1` - type of `1` is context-dependent
        is KtConstantExpression -> initializer.getClassId()?.let { buildClassType(it) }?.isSubTypeOf(typeReference.getKtType()) == true
        is KtCallExpression -> initializer.typeArgumentList != null || !returnTypeOfCallDependsOnTypeParameters(initializer)
        is KtArrayAccessExpression -> !returnTypeOfCallDependsOnTypeParameters(initializer)
        is KtCallableReferenceExpression -> isCallableReferenceExpressionTypeContextIndependent(initializer)
        is KtQualifiedExpression -> initializer.callExpression?.let { isInitializerTypeContextIndependent(it, typeReference) } == true
        is KtLambdaExpression -> isLambdaExpressionTypeContextIndependent(initializer, typeReference)
        is KtNamedFunction -> isAnonymousFunctionTypeContextIndependent(initializer, typeReference)
        is KtSimpleNameExpression -> true

        // consider types of expressions that the compiler views as constants, e.g. `1 + 2`, as independent
        else -> initializer.evaluate(KtConstantEvaluationMode.CONSTANT_EXPRESSION_EVALUATION) != null
    }

    context(KtAnalysisSession)
    private fun isLambdaExpressionTypeContextIndependent(lambdaExpression: KtLambdaExpression, typeReference: KtTypeReference): Boolean {
        val lastStatement = lambdaExpression.bodyExpression?.statements?.lastOrNull() ?: return false

        val returnTypeReference = (typeReference.typeElement as? KtFunctionType)?.returnTypeReference ?: return false
        return isInitializerTypeContextIndependent(lastStatement, returnTypeReference)
    }

    context(KtAnalysisSession)
    private fun isAnonymousFunctionTypeContextIndependent(anonymousFunction: KtNamedFunction, typeReference: KtTypeReference): Boolean {
        if (anonymousFunction.hasDeclaredReturnType() || anonymousFunction.hasBlockBody()) return true

        val returnTypeReference = (typeReference.typeElement as? KtFunctionType)?.returnTypeReference ?: return false
        return anonymousFunction.initializer?.let { isInitializerTypeContextIndependent(it, returnTypeReference) } == true
    }

    context(KtAnalysisSession)
    private fun isCallableReferenceExpressionTypeContextIndependent(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
        val resolved = callableReferenceExpression.callableReference.references.firstNotNullOfOrNull { it.resolve() } ?: return false
        if (resolved !is KtNamedFunction) return true

        val symbol = resolved.getFunctionLikeSymbol()

        val typeParameters = symbol.typeParameters
        if (typeParameters.isEmpty()) return true

        val receiverType = symbol.receiverType ?: return false
        return typeParameters.all { typeReferencesTypeParameter(it, receiverType) }
    }

    private val KtDeclaration.isVar: Boolean
        get() {
            val property = (this as? KtProperty) ?: (this as? KtPropertyAccessor)?.property
            return property?.isVar == true
        }

    override fun getFamilyName(): String = KotlinBundle.message("remove.explicit.type.specification")

    override fun invoke(
      actionContext: ActionContext,
      element: KtDeclaration,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        element.removeTypeReference()
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
}