// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyzeCopy
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.projectStructure.KaDanglingFileResolutionMode
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.analysis.api.utils.findSamSymbolOrNull
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaceSamConstructorCall
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.refactoring.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.refactoring.moveFunctionLiteralOutsideParentheses
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object AddFunModifierFixFactory {
    val addFunModifierFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.InterfaceAsFunction ->
        listOfNotNull(createQuickFix(diagnostic))
    }

    context(_: KaSession)
    private fun createQuickFix(diagnostic: KaFirDiagnostic.InterfaceAsFunction): ModCommandAction? {
        val referrer = diagnostic.psi
        if (referrer.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_4) return null

        val referrerCall = referrer.parent as? KtCallExpression ?: return null
        if (referrerCall.valueArguments.singleOrNull() !is KtLambdaArgument) return null
        val referenceClassSymbol = diagnostic.classSymbol as? KaNamedClassSymbol ?: return null
        if (referenceClassSymbol.isFun || referenceClassSymbol.findSamSymbolOrNull() == null) return null

        val referenceClass = referenceClassSymbol.psi as? KtClass ?: return null
        val referenceClassName = referenceClass.name ?: return null

        return AddFunModifierFix(
            referenceClass,
            ElementContext(referrerCall.createSmartPointer()),
            referenceClassName,
        )
    }

    private data class ElementContext(
        val referrerCallPointer: SmartPsiElementPointer<KtCallExpression>,
    )

    private class AddFunModifierFix(
        element: KtClass,
        elementContext: ElementContext,
        private val elementName: String,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtClass, ElementContext>(element, elementContext) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtClass,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            val referrerCall = updater.getWritable(elementContext.referrerCallPointer.element)
            element.addModifier(KtTokens.FUN_KEYWORD)
            if (referrerCall?.lambdaArguments?.singleOrNull() == null) return
            val argument = referrerCall.getStrictParentOfType<KtValueArgument>()
                ?.takeIf { it.getArgumentExpression() == referrerCall }
                ?: return
            val parentCall = argument.getStrictParentOfType<KtCallExpression>() ?: return
            replaceSamConstructorCall(referrerCall)
            analyzeCopy(element, resolutionMode = KaDanglingFileResolutionMode.PREFER_SELF) {
                if (parentCall.canMoveLambdaOutsideParentheses(skipComplexCalls = true)) {
                    parentCall.moveFunctionLiteralOutsideParentheses()
                }
            }
        }

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("add.fun.modifier.to.0", elementName)
    }
}