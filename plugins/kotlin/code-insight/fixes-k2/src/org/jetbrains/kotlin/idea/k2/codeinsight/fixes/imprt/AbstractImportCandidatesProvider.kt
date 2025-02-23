// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFileSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.idea.base.util.isImported
import org.jetbrains.kotlin.idea.util.positionContext.KDocLinkNamePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinTypeNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.resolve.ImportPath

internal abstract class AbstractImportCandidatesProvider(): ImportCandidatesProvider {
    protected abstract val positionContext: KotlinRawPositionContext

    private val file: KtFile get() = positionContext.position.containingFile as KtFile
    private val fileImports: List<ImportPath> by lazy { file.importDirectives.mapNotNull { it.importPath } }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    protected fun ImportCandidate.isVisible(visibilityChecker: KaUseSiteVisibilityChecker): Boolean = 
        when (this) {
            is CallableImportCandidate -> symbol.isVisible(visibilityChecker) && dispatcherObject?.isVisible(visibilityChecker) != false
            is ClassLikeImportCandidate -> symbol.isVisible(visibilityChecker)
        }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun KaSymbol.isVisible(visibilityChecker: KaUseSiteVisibilityChecker): Boolean =
        this is KaDeclarationSymbol && visibilityChecker.isVisible(this)

    protected fun PsiElement.isImported(): Boolean =
        kotlinFqName?.let { ImportPath(it, isAllUnder = false).isImported(fileImports, excludedFqNames = emptyList()) } == true

    protected fun PsiMember.canBeImported(): Boolean {
        return when (this) {
            is PsiClass -> qualifiedName != null && (containingClass == null || hasModifier(JvmModifier.STATIC) || positionContext.acceptsInnerClasses())
            is PsiField, is PsiMethod -> hasModifier(JvmModifier.STATIC) && containingClass?.qualifiedName != null
            else -> false
        }
    }

    protected fun KtDeclaration.canBeImported(): Boolean {
        return when (this) {
            is KtProperty -> isTopLevel || containingClassOrObject is KtObjectDeclaration
            is KtNamedFunction -> isTopLevel || containingClassOrObject is KtObjectDeclaration
            is KtTypeAlias -> true
            is KtClassOrObject -> !isLocal && (!isInner || positionContext.acceptsInnerClasses())

            else -> false
        }
    }

    context(KaSession)
    protected fun getFileSymbol(): KaFileSymbol = file.symbol

    private val KtClassLikeDeclaration.isInner: Boolean get() = hasModifier(KtTokens.INNER_KEYWORD)

    private fun KotlinRawPositionContext.acceptsInnerClasses(): Boolean =
        this is KotlinTypeNameReferencePositionContext || this is KDocLinkNamePositionContext
}