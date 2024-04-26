// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithModality
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaceSamConstructorCall
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddFunModifierFixFactory {
    val addFunModifierFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.InterfaceAsFunction ->
        val referrer = diagnostic.psi
        if (referrer.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_4) return@ModCommandBased emptyList()

        val referrerCall = referrer.parent as? KtCallExpression ?: return@ModCommandBased emptyList()
        if (referrerCall.valueArguments.singleOrNull() !is KtLambdaArgument) return@ModCommandBased emptyList()
        val referenceClassSymbol = diagnostic.classSymbol as? KtNamedClassOrObjectSymbol ?: return@ModCommandBased emptyList()
        if (referenceClassSymbol.isFun || !referenceClassSymbol.isSamInterface()) return@ModCommandBased emptyList()

        val referenceClass = referenceClassSymbol.psi as? KtClass ?: return@ModCommandBased emptyList()
        val referenceClassName = referenceClass.name ?: return@ModCommandBased emptyList()

        return@ModCommandBased listOf(
            AddFunModifierFix(
                referenceClass,
                referenceClassName,
                ElementContext(referrerCall.createSmartPointer())
            )
        )
    }

    private data class ElementContext(
        val referrerCallPointer: SmartPsiElementPointer<KtCallExpression>,
    )

    private class AddFunModifierFix(ktClass: KtClass, private val elementName: String, context: ElementContext) :
        KotlinPsiUpdateModCommandAction.ElementBased<KtClass, ElementContext>(ktClass, context) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtClass,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val referrerCall = updater.getWritable(elementContext.referrerCallPointer.element)
            element.addModifier(KtTokens.FUN_KEYWORD)
            if (referrerCall?.lambdaArguments?.singleOrNull() == null) return
            referrerCall.getStrictParentOfType<KtValueArgument>()?.takeIf { it.getArgumentExpression() == referrerCall } ?: return
            replaceSamConstructorCall(referrerCall)
        }

        override fun getFamilyName() = KotlinBundle.message("add.fun.modifier.to.0", elementName)
    }
}

context(KtAnalysisSession)
private fun KtNamedClassOrObjectSymbol.isSamInterface(): Boolean {
    if (classKind != KtClassKind.INTERFACE) return false
    val singleAbstractMember = getMemberScope()
        .getCallableSymbols()
        .filterIsInstance<KtSymbolWithModality>()
        .filter { it.modality == Modality.ABSTRACT }
        .singleOrNull() ?: return false
    return singleAbstractMember is KtFunctionSymbol && singleAbstractMember.typeParameters.isEmpty()
}