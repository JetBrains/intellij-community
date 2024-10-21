// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.checkers

import com.intellij.platform.ide.progress.ModalTaskOwner.project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.util.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.completion.impl.k2.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue

@OptIn(KaExperimentalApi::class)
internal class CompletionVisibilityChecker(
    private val basicContext: FirBasicCompletionContext,
    private val positionContext: KotlinRawPositionContext
) {
    fun isDefinitelyInvisibleByPsi(declaration: KtDeclaration): Boolean = forbidAnalysis("isDefinitelyInvisibleByPsi") {
        if (basicContext.parameters.invocationCount >= 2) return false
        if (basicContext.originalKtFile is KtCodeFragment) return false

        val declarationContainingFile = declaration.containingKtFile
        if (declaration.isPrivate()) {
            if (declarationContainingFile != basicContext.originalKtFile && declarationContainingFile != basicContext.fakeKtFile) {
                return true
            }
        }
        if (declaration.hasModifier(KtTokens.INTERNAL_KEYWORD)) {
            return !canAccessInternalDeclarationsFromFile(declarationContainingFile)
        }

        return false
    }

    private fun canAccessInternalDeclarationsFromFile(file: KtFile): Boolean {
        if (file.isCompiled) {
            return false
        }
        val useSiteModule = basicContext.useSiteModule

        val declarationModule = file.getKaModule(basicContext.project, useSiteModule)

        return declarationModule == useSiteModule ||
                declarationModule in basicContext.useSiteModule.directFriendDependencies
    }

    context(KaSession)
    fun isVisible(symbol: KaDeclarationSymbol): Boolean {
        if (positionContext is KDocNameReferencePositionContext) return true

        // Don't offer any deprecated items that could lead to compile errors.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return false

        if (basicContext.parameters.invocationCount > 1) return true

        if (symbol is KaClassLikeSymbol) {
            val classId = (symbol as? KaClassLikeSymbol)?.classId
            if (classId?.asSingleFqName()?.isJavaClassNotToBeUsedInKotlin() == true) return false
        }

        if (basicContext.originalKtFile is KtCodeFragment) return true

        return isVisible(
            symbol,
            basicContext.originalKtFile.symbol,
            (positionContext as? KotlinSimpleNameReferencePositionContext)?.explicitReceiver,
            positionContext.position
        )
    }
}