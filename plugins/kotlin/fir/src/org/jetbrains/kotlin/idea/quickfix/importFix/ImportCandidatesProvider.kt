// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.importFix

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

internal abstract class ImportCandidatesProvider(
    protected val indexProvider: KtSymbolFromIndexProvider,
) {
    protected abstract val positionContext: KotlinNameReferencePositionContext

    context(KtAnalysisSession)
    protected fun KtSymbol.isVisible(fileSymbol: KtFileSymbol): Boolean =
        this is KtSymbolWithVisibility && isVisible(this, fileSymbol, receiverExpression = null, positionContext.position)

    protected fun PsiMember.canBeImported(): Boolean {
        return when (this) {
            is PsiClass -> qualifiedName != null && (containingClass == null || hasModifier(JvmModifier.STATIC))
            is PsiField, is PsiMethod -> hasModifier(JvmModifier.STATIC) && containingClass?.qualifiedName != null
            else -> false
        }
    }

    protected fun KtDeclaration.canBeImported(): Boolean {
        return when (this) {
            is KtProperty -> isTopLevel || containingClassOrObject is KtObjectDeclaration
            is KtNamedFunction -> isTopLevel || containingClassOrObject is KtObjectDeclaration
            is KtClassLikeDeclaration ->
                getClassId() != null && parentsOfType<KtClassLikeDeclaration>(withSelf = true).none { it.isInner }

            else -> false
        }
    }

    context(KtAnalysisSession)
    protected fun getFileSymbol(): KtFileSymbol = positionContext.nameExpression.containingKtFile.getFileSymbol()

    private val KtClassLikeDeclaration.isInner: Boolean get() = hasModifier(KtTokens.INNER_KEYWORD)

    context(KtAnalysisSession)
    abstract fun collectCandidates(): List<KtDeclarationSymbol>
}