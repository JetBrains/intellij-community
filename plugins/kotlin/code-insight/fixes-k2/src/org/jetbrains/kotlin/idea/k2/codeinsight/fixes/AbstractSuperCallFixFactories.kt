// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.BUNDLE
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.codeinsight.utils.AbstractSuperCallFixUtils.addSuperTypeListEntryIfNotExists
import org.jetbrains.kotlin.idea.codeinsight.utils.AbstractSuperCallFixUtils.getParentSuperExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.AbstractSuperCallFixUtils.specifySuperType
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.UpdateToCorrectMethodFix.Method.Companion.isAnyEquals
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.UpdateToCorrectMethodFix.Method.Companion.isAnyHashCode
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.UpdateToCorrectMethodFix.Method.Companion.isAnyToString
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.GenerateEqualsAndHashCodeUtils
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.Info
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinToStringTemplatesManager
import org.jetbrains.kotlin.idea.k2.codeinsight.generate.KotlinToStringTemplatesManager.Companion.DEFAULT_SINGLE
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object AbstractSuperCallFixFactories {
    val errorFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AbstractSuperCall ->
        listOfNotNull(createQuickFix(diagnostic.psi))
    }

    val warningFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.AbstractSuperCallWarning ->
        listOfNotNull(createQuickFix(diagnostic.psi))
    }

    private fun KaSession.createQuickFix(element: PsiElement): ModCommandAction? {
        val expression = element as? KtNameReferenceExpression ?: return null
        val containingClass = expression.getNonStrictParentOfType<KtClassOrObject>() ?: return null
        val containingFunction = expression.parentOfType<KtNamedFunction>() ?: return null
        val functionSymbol = expression.resolveToCall()?.successfulCallOrNull<KaSimpleFunctionCall>()?.symbol ?: return null

        fun KaSession.computeInfoIfNotInObject(containingClass: KtClassOrObject): Info? {
            if (containingClass !is KtClass) return null
            val variablesForEquals = GenerateEqualsAndHashCodeUtils.getPropertiesToUseInGeneratedMember(containingClass)
            return Info(containingClass, variablesForEquals, variablesForEquals, equalsInClass = null, hashCodeInClass = null)
        }

        return when {
            functionSymbol.isAnyEquals(this.useSiteSession) -> {
                val info = computeInfoIfNotInObject(containingClass) ?: return null
                val generatedEquals = GenerateEqualsAndHashCodeUtils.generateEquals(info, tryToFindEqualsMethodForClass = false)
                                      ?: return null
                val elementContext = UpdateToCorrectMethodFix.ElementContext(generatedEquals)
                UpdateToCorrectMethodFix(containingFunction, elementContext, UpdateToCorrectMethodFix.Method.EQUALS)
            }
            functionSymbol.isAnyHashCode(this.useSiteSession) -> {
                val info = computeInfoIfNotInObject(containingClass) ?: return null
                val generatedHashCode = GenerateEqualsAndHashCodeUtils.generateHashCode(info, tryToFindHashCodeMethodForClass = false)
                                        ?: return null
                val elementContext = UpdateToCorrectMethodFix.ElementContext(generatedHashCode)
                UpdateToCorrectMethodFix(containingFunction, elementContext, UpdateToCorrectMethodFix.Method.HASH_CODE)
            }
            functionSymbol.isAnyToString(this.useSiteSession) -> {
                val template = TemplateResource(
                    KotlinBundle.message("action.generate.tostring.template.single"),
                    KotlinToStringTemplatesManager.readFile(DEFAULT_SINGLE),
                    true,
                ).template ?: return null

                val generatedToString = GenerateEqualsAndHashCodeUtils.generateToString(
                    containingClass,
                    emptyList(),
                    template,
                ) ?: return null

                val elementContext = UpdateToCorrectMethodFix.ElementContext(generatedToString)
                UpdateToCorrectMethodFix(containingFunction, elementContext, UpdateToCorrectMethodFix.Method.TO_STRING)
            }
            else -> {
                val superExpression = expression.getParentSuperExpression() ?: return null
                val superType = getSuperClassFqNameToReferTo(expression) ?: return null
                SpecifySuperTypeExplicitlyFix(superExpression, superType)
            }
        }
    }
}

private class UpdateToCorrectMethodFix(
    element: KtNamedFunction,
    elementContext: ElementContext,
    private val method: Method,
) : KotlinPsiUpdateModCommandAction.ElementBased<KtNamedFunction, UpdateToCorrectMethodFix.ElementContext>(element, elementContext) {

    data class ElementContext(
        val generatedFunction: KtNamedFunction,
    )

    enum class Method(
        val callableId: CallableId,
        @field:PropertyKey(resourceBundle = BUNDLE) val messageKey: String,
    ) {
        EQUALS(CallableId(StandardClassIds.Any, Name.identifier("equals")), "equals.text"),
        HASH_CODE(CallableId(StandardClassIds.Any, Name.identifier("hashCode")), "hash.code.text"),
        TO_STRING(CallableId(StandardClassIds.Any, Name.identifier("toString")), "action.generate.tostring.name");

        companion object {
            fun KaCallableSymbol.isAnyEquals(analysisSession: KaSession): Boolean =
                isOverride(EQUALS.callableId, analysisSession)

            fun KaCallableSymbol.isAnyHashCode(analysisSession: KaSession): Boolean =
                isOverride(HASH_CODE.callableId, analysisSession)

            fun KaCallableSymbol.isAnyToString(analysisSession: KaSession): Boolean =
                isOverride(TO_STRING.callableId, analysisSession)
        }
    }

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message(method.messageKey)

    override fun invoke(
        actionContext: ActionContext,
        element: KtNamedFunction,
        elementContext: ElementContext,
        updater: ModPsiUpdater,
    ) {
        element.replace(elementContext.generatedFunction)?.let {
            shortenReferences(it as KtElement)
        }
    }
}

private class SpecifySuperTypeExplicitlyFix(
    element: KtSuperExpression,
    private val superType: FqName,
) : PsiUpdateModCommandAction<KtSuperExpression>(element) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("action.generate.super.type")

    override fun getPresentation(
        context: ActionContext,
        element: KtSuperExpression,
    ): Presentation {
        val actionName = KotlinBundle.message("specify.super.type", superType.shortName())
        return Presentation.of(actionName)
    }

    override fun invoke(
        context: ActionContext,
        element: KtSuperExpression,
        updater: ModPsiUpdater,
    ) {
        val containingClass = element.getNonStrictParentOfType<KtClassOrObject>() ?: return
        element.specifySuperType(superType)
        containingClass.addSuperTypeListEntryIfNotExists(superType)
    }
}

private fun KaSession.getSuperClassFqNameToReferTo(expression: KtNameReferenceExpression): FqName? {
    fun tryViaCalledFunction(): KaCallableSymbol? = expression.resolveToCall()
        ?.successfulCallOrNull<KaSimpleFunctionCall>()
        ?.symbol
        ?.allOverriddenSymbols?.find { (it as? KaFunctionSymbol)?.modality != KaSymbolModality.ABSTRACT }

    fun tryViaContainingFunction(): KaCallableSymbol? = expression.containingFunction()
        ?.symbol
        ?.allOverriddenSymbols
        ?.find { (it as? KaFunctionSymbol)?.modality != KaSymbolModality.ABSTRACT }

    val callableToUseInstead = tryViaCalledFunction()
                               ?: tryViaContainingFunction()
                               ?: return null

    return callableToUseInstead.containingDeclaration?.importableFqName
}

private fun KaCallableSymbol.isOverride(
    callableId: CallableId,
    analysisSession: KaSession,
): Boolean = with(analysisSession) {
    allOverriddenSymbolsWithSelf.any { it.callableId == callableId }
}
