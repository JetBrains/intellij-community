// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtCallableMemberCall
import org.jetbrains.kotlin.analysis.api.calls.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal object AddDataModifierFixFactory {
    val addDataModifierFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.ComponentFunctionMissing ->
        val element = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()

        val callableSymbol = if (element is KtParameter && element.firstChild is KtDestructuringDeclaration) {
            (element as? KtParameter)?.getParameterSymbol()
        } else {
            element.resolveCall()?.successfulCallOrNull<KtCallableMemberCall<*, *>>()?.symbol
        }

        val type = (callableSymbol?.returnType as? KtNonErrorClassType)?.ownTypeArguments?.firstOrNull()?.type
            ?: callableSymbol?.returnType

        val classSymbol = (type as? KtNonErrorClassType)?.classSymbol as? KtNamedClassOrObjectSymbol
            ?: return@ModCommandBased emptyList()

        val modality = classSymbol.modality
        if (modality != Modality.FINAL || classSymbol.isInner) return@ModCommandBased emptyList()
        val constructors = classSymbol.getDeclaredMemberScope().getConstructors()
        val ctorParams = constructors.firstOrNull { it.isPrimary }?.valueParameters ?: return@ModCommandBased emptyList()
        if (ctorParams.isEmpty()) return@ModCommandBased emptyList()

        if (!ctorParams.all {
                if (it.isVararg) return@all false
                val property = it.generatedPrimaryConstructorProperty ?: return@all false
                return@all property.isVisible(element)
            }
        ) return@ModCommandBased emptyList()

        val ktClass = classSymbol.psi as? KtClass ?: return@ModCommandBased emptyList()
        val fqName = classSymbol.classIdIfNonLocal?.asSingleFqName()?.asString() ?: return@ModCommandBased emptyList()
        listOfNotNull(AddDataModifierFix(ktClass, ElementContext(fqName)))
    }

    private data class ElementContext(
        val fqName: String,
    )

    private class AddDataModifierFix(
        ktClass: KtClass,
        private val context: ElementContext,
    ) : KotlinModCommandAction.ElementBased<KtClass, ElementContext>(ktClass, context) {

        override fun invoke(
            context: ActionContext,
            element: KtClass,
            elementContext: ElementContext,
            updater: ModPsiUpdater,
        ) {
            element.addModifier(KtTokens.DATA_KEYWORD)
        }

        override fun getFamilyName(): String {
            return KotlinBundle.message("fix.make.data.class", context.fqName)
        }
    }
}

context(KtAnalysisSession)
private fun KtSymbolWithVisibility.isVisible(position: KtElement): Boolean {
    val file = position.containingKtFile.getFileSymbol()
    return isVisible(this, file, position = position)
}