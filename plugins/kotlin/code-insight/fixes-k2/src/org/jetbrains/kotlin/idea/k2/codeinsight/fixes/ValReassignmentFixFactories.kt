// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AssignToPropertyFix
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object ValReassignmentFixFactories {

    val assignToPropertyFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValReassignment ->
        val expression = diagnostic.psi as? KtNameReferenceExpression ?: return@ModCommandBased emptyList()
        val containingClass = expression.containingClass() ?: return@ModCommandBased emptyList()
        val right = (expression.parent as? KtBinaryExpression)?.right ?: return@ModCommandBased emptyList()
        val type = right.expressionType ?: return@ModCommandBased emptyList()
        val name = expression.getReferencedNameAsName()

        val inSecondaryConstructor = expression.getStrictParentOfType<KtSecondaryConstructor>() != null
        val hasAssignableProperty = containingClass.getProperties().any {
            (inSecondaryConstructor || it.isVar) && it.hasNameAndTypeOf(name, type)
        }
        val hasAssignablePropertyInPrimaryConstructor = containingClass.primaryConstructor?.valueParameters?.any {
            it.valOrVarKeyword?.node?.elementType == KtTokens.VAR_KEYWORD && it.hasNameAndTypeOf(name, type)
        } ?: false

        if (!hasAssignableProperty && !hasAssignablePropertyInPrimaryConstructor) return@ModCommandBased emptyList()
        val hasSingleImplicitReceiver = expression.containingKtFile.scopeContext(expression).implicitReceivers.size == 1

        listOf(
            AssignToPropertyFix(expression, hasSingleImplicitReceiver)
        )
    }
}

context(_: KaSession)
private fun KtCallableDeclaration.hasNameAndTypeOf(name: Name, type: KaType) =
    nameAsName == name && (symbol as? KaCallableSymbol)?.returnType == type
