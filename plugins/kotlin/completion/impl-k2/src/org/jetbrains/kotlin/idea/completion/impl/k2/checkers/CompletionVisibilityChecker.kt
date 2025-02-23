// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.checkers

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.util.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters.Companion.useSiteModule
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
    private val parameters: KotlinFirCompletionParameters, // should be the only parameter
) {

    // There should be only a single KotlinRawPositionContext throughout the entire completion.
    // However, since the completion API allows passing any of [KotlinRawPositionContext] around,
    // we will cache all of them  just in case.
    private val visibilityCheckerPerPositionContextCache = mutableMapOf<KotlinRawPositionContext, KaUseSiteVisibilityChecker>()

    fun canBeVisible(declaration: KtDeclaration): Boolean = forbidAnalysis("canBeVisible") {
        val originalFile = parameters.originalFile
        if (originalFile is KtCodeFragment) return true

        // todo should be > 2
        if (parameters.invocationCount >= 2) return true

        val declarationContainingFile = declaration.containingKtFile
        // todo
        //   class Outer {
        //     private class Inner {
        //       fun member() {}
        //     }
        //   }
        //  in this example the member itself if neither private or internal,
        //  but the parent is.
        return if (declaration.isPrivate()
            && declarationContainingFile != originalFile
            && declarationContainingFile != parameters.completionFile
        ) false
        else if (declaration.hasModifier(KtTokens.INTERNAL_KEYWORD))
            canAccessInternalDeclarationsFromFile(declarationContainingFile)
        else true
    }

    private fun canAccessInternalDeclarationsFromFile(file: KtFile): Boolean {
        if (file.isCompiled) {
            return false
        }
        val useSiteModule = parameters.useSiteModule
        val declarationModule = file.getKaModule(parameters.originalFile.project, useSiteModule)

        return declarationModule == useSiteModule ||
                declarationModule in useSiteModule.directFriendDependencies
    }

    context(KaSession)
    fun isVisible(
        symbol: KaDeclarationSymbol,
        positionContext: KotlinRawPositionContext,
    ): Boolean {
        if (positionContext is KDocNameReferencePositionContext) return true

        // Don't offer any deprecated items that could lead to compile errors.
        if (symbol.deprecationStatus?.deprecationLevel == DeprecationLevelValue.HIDDEN) return false

        if (parameters.invocationCount > 1) return true

        if ((symbol as? KaClassLikeSymbol)
                ?.classId
                ?.asSingleFqName()
                ?.isJavaClassNotToBeUsedInKotlin() == true
        ) return false

        val originalFile = parameters.originalFile
        if (originalFile is KtCodeFragment) return true

        return getCachedVisibilityChecker(positionContext).isVisible(symbol)
    }

    context(KaSession)
    private fun getCachedVisibilityChecker(positionContext: KotlinRawPositionContext): KaUseSiteVisibilityChecker {
        return visibilityCheckerPerPositionContextCache.getOrPut(positionContext) {
            createUseSiteVisibilityChecker(
                useSiteFile = parameters.originalFile.symbol,
                receiverExpression = (positionContext as? KotlinSimpleNameReferencePositionContext)?.explicitReceiver,
                position = positionContext.position,
            )
        }
    }
}