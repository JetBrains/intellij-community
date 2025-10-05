// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.scope
import org.jetbrains.kotlin.analysis.api.components.scopeContext
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.base.codeInsight.ExpectedExpressionMatcherProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher
import org.jetbrains.kotlin.idea.quickfix.AbstractRenameUnresolvedReferenceFix
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
internal fun createRenameUnresolvedReferenceFix(element: KtNameReferenceExpression): IntentionAction? {

    val isCallee = element.isCallee()
    val receiver = element.getReceiverExpression()

    val callables = if (receiver != null) {
        receiver.expressionType?.scope?.getCallableSignatures()?.map { it.symbol } ?: return null
    } else {
        element.containingKtFile.scopeContext(element).compositeScope { it !is KaScopeKind.ImportingScope }.callables
    }

    val matcher = ExpectedExpressionMatcherProvider[element]
    val targetVariables = callables
        .filter { if (isCallee) it is KaFunctionSymbol else it is KaVariableSymbol }
        .filter { matcher == null || matcher.match(it.returnType) }
        .mapNotNull { callableSymbol -> callableSymbol.name }
        .toList()

    val patternExpression = element.getQualifiedElement() as? KtExpression ?: return null
    val container = element.parents.firstOrNull { it is KtDeclarationWithBody || it is KtClassOrObject || it is KtFile } as? KtElement ?: return null
    val occurrences = K2SemanticMatcher.findMatches(patternElement = patternExpression, scopeElement = container)
        .mapNotNull { match ->
            val candidate = match.getQualifiedElementSelector() as? KtNameReferenceExpression
            if (candidate != null && candidate.isCallee() == isCallee) candidate.createSmartPointer() else null
        }

    return RenameUnresolvedReferenceFix(element, targetVariables, occurrences)
}

internal object ExpectedReferenceFoundPackageFixFactory {
    val renameFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ExpressionExpectedPackageFound ->
        val expr = diagnostic.psi as? KtNameReferenceExpression ?: return@IntentionBased emptyList()
        return@IntentionBased listOfNotNull(createRenameUnresolvedReferenceFix(expr))
    }
}

private class RenameUnresolvedReferenceFix(
    element: KtNameReferenceExpression,
    private val targetCandidates: List<Name>,
    private val occurrences: List<SmartPsiElementPointer<KtNameReferenceExpression>>
) : AbstractRenameUnresolvedReferenceFix(element) {
    override fun KtExpression.findOccurrences(
        container: KtElement,
        isCallee: Boolean
    ): List<KtNameReferenceExpression> {
        return occurrences.mapNotNull { it.element }
    }

    override fun KtExpression.getTargetCandidates(element: KtNameReferenceExpression): Array<LookupElementBuilder> = targetCandidates.map {
        LookupElementBuilder.create(it)
    }.toTypedArray()
}
