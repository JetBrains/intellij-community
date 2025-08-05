// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.components.evaluate
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isPublicApi
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.components.returnType
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.components.type
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
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
import org.jetbrains.kotlin.psi.psiUtil.inferClassIdByPsi
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

    override fun isApplicableByPsi(element: KtDeclaration): Boolean = canExplicitTypeBeRemoved(element)

    override fun KaSession.prepareContext(element: KtDeclaration): Unit? = when {
        element is KtParameter -> true
        element is KtNamedFunction && element.hasBlockBody() -> element.returnType.isUnitType
        element is KtNamedFunction && element.isRecursive() -> false
        element is KtCallableDeclaration && publicReturnTypeShouldBePresentInApiMode(element) -> false
        else -> !element.isExplicitTypeReferenceNeededForTypeInferenceByAnalyze()
    }.asUnit

    context(_: KaSession)
    private fun publicReturnTypeShouldBePresentInApiMode(declaration: KtCallableDeclaration): Boolean {
        if (declaration.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) == ExplicitApiMode.DISABLED) return false

        if (declaration.containingClassOrObject?.isLocal == true) return false
        if (declaration is KtFunction && declaration.isLocal) return false
        if (declaration is KtProperty && declaration.isLocal) return false

        return isPublicApi(declaration.symbol)
    }

    context(_: KaSession)
    private fun KtDeclaration.isExplicitTypeReferenceNeededForTypeInferenceByAnalyze(): Boolean {
        val typeReference = typeReference ?: return false
        val initializer = getInitializerOrGetterInitializer() ?: return true
        val explicitType = returnType

        // The initializer may require an explicit type, but an error type is definitely not it. The intention makes a conscious decision to
        // allow the user to quickly remove completely erroneous explicit types.
        //
        // The situation is more fuzzy with errors in type arguments. Here, it makes sense not to remove the type, but rather to fix it. And
        // with a little bit of type information, the intention has a better chance of deciding whether the initializer needs that explicit
        // type. So we don't check the `explicitType` for nested errors.
        if (explicitType is KaErrorType) return false

        if (!isInitializerTypeContextIndependent(initializer, typeReference)) return true

        val initializerType = initializer.expressionType ?: return true
        val typeCanBeRemoved = if (isVar) {
            initializerType.semanticallyEquals(explicitType)
        } else {
            initializerType.isSubtypeOf(explicitType)
        }
        return !typeCanBeRemoved
    }

    /**
     * Currently we don't use on-air resolve in the implementation, therefore the function might return false negative results
     * for expressions that are not covered.
     */
    context(_: KaSession)
    private fun isInitializerTypeContextIndependent(
        initializer: KtExpression,
        typeReference: KtTypeReference,
    ): Boolean = when (initializer) {
        is KtStringTemplateExpression -> true
        // `val n: Int = 1` - type of `1` is context-independent
        // `val n: Long = 1` - type of `1` is context-dependent
        is KtConstantExpression -> {
            val classId = initializer.inferClassIdByPsi()
            val let = classId?.let { buildClassType(it) }
            val superType = typeReference.type
            val subTypeOf = let?.isSubtypeOf(superType)
            subTypeOf == true
        }
        is KtCallExpression -> initializer.typeArgumentList != null || !returnTypeOfCallDependsOnTypeParameters(initializer)
        is KtArrayAccessExpression -> !returnTypeOfCallDependsOnTypeParameters(initializer)
        is KtCallableReferenceExpression -> isCallableReferenceExpressionTypeContextIndependent(initializer)
        is KtQualifiedExpression -> ((initializer as? KtDotQualifiedExpression)?.selectorExpression ?: initializer.callExpression)?.let { isInitializerTypeContextIndependent(it, typeReference) } == true
        is KtLambdaExpression -> isLambdaExpressionTypeContextIndependent(initializer, typeReference)
        is KtNamedFunction -> isAnonymousFunctionTypeContextIndependent(initializer, typeReference)
        is KtSimpleNameExpression, is KtBinaryExpression -> true
        is KtIfExpression -> {
            val type = typeReference.type
            val thenType = initializer.then?.expressionType
            val elseType = initializer.`else`?.expressionType
            thenType != null && elseType != null && type.semanticallyEquals(thenType) && type.semanticallyEquals(elseType)
        }
        is KtWhenExpression -> {
            val type = typeReference.type
            initializer.entries.all {
                val expressionType = it.expression?.expressionType ?: return@all false
                expressionType.semanticallyEquals(type)
            }
        }

        // consider types of expressions that the compiler views as constants, e.g. `1 + 2`, as independent
        else -> initializer.evaluate() != null
    }

    context(_: KaSession)
    private fun isLambdaExpressionTypeContextIndependent(lambdaExpression: KtLambdaExpression, typeReference: KtTypeReference): Boolean {
        val lastStatement = lambdaExpression.bodyExpression?.statements?.lastOrNull() ?: return false

        val returnTypeReference = (typeReference.typeElement as? KtFunctionType)?.returnTypeReference ?: return false
        return isInitializerTypeContextIndependent(lastStatement, returnTypeReference)
    }

    context(_: KaSession)
    private fun isAnonymousFunctionTypeContextIndependent(anonymousFunction: KtNamedFunction, typeReference: KtTypeReference): Boolean {
        if (anonymousFunction.hasDeclaredReturnType() || anonymousFunction.hasBlockBody()) return true

        val returnTypeReference = (typeReference.typeElement as? KtFunctionType)?.returnTypeReference ?: return false
        return anonymousFunction.initializer?.let { isInitializerTypeContextIndependent(it, returnTypeReference) } == true
    }

    context(_: KaSession)
    private fun isCallableReferenceExpressionTypeContextIndependent(callableReferenceExpression: KtCallableReferenceExpression): Boolean {
        val resolved = callableReferenceExpression.callableReference.references.firstNotNullOfOrNull { it.resolve() } ?: return false
        if (resolved !is KtNamedFunction) return true

        val symbol = resolved.symbol

        @OptIn(KaExperimentalApi::class)
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
        element.removeDeclarationTypeReference()
    }
}