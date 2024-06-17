// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaSymbolWithVisibility
import org.jetbrains.kotlin.analysis.api.types.KaNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal object AddDataModifierFixFactory {
    val addDataModifierFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ComponentFunctionMissing ->
        val element = diagnostic.psi as? KtExpression ?: return@ModCommandBased emptyList()

        val callableSymbol = if (element is KtParameter && element.firstChild is KtDestructuringDeclaration) {
            (element as? KtParameter)?.getParameterSymbol()
        } else {
            element.resolveCallOld()?.successfulCallOrNull<KaCallableMemberCall<*, *>>()?.symbol
        }

        val type = (callableSymbol?.returnType as? KtNonErrorClassType)?.typeArguments?.firstOrNull()?.type
            ?: callableSymbol?.returnType

        val classSymbol = (type as? KaNonErrorClassType)?.symbol as? KaNamedClassOrObjectSymbol
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
        val fqName = classSymbol.classId?.asSingleFqName()?.asString() ?: return@ModCommandBased emptyList()
        listOfNotNull(AddDataModifierFix(ktClass, ElementContext(fqName)))
    }

    private data class ElementContext(
        val fqName: String,
    )

    private class AddDataModifierFix(
        ktClass: KtClass,
        private val context: ElementContext,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtClass, ElementContext>(ktClass, context) {

        override fun invoke(
            actionContext: ActionContext,
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

context(KaSession)
private fun KaSymbolWithVisibility.isVisible(position: KtElement): Boolean {
    val file = position.containingKtFile.getFileSymbol()
    return isVisible(this, file, position = position)
}