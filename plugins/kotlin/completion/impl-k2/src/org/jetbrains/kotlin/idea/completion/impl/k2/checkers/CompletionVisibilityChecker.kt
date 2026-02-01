// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.impl.k2.checkers

import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.components.createUseSiteVisibilityChecker
import org.jetbrains.kotlin.analysis.api.components.deprecationStatus
import org.jetbrains.kotlin.analysis.api.permissions.forbidAnalysis
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.base.util.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinFirCompletionParameters.Companion.useSiteModule
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.deprecation.DeprecationLevelValue

@OptIn(KaExperimentalApi::class)
internal class CompletionVisibilityChecker(
    private val parameters: KotlinFirCompletionParameters, // should be the only parameter
) {

    // There should be only a single KotlinRawPositionContext throughout the entire completion.
    // However, since the completion API allows passing any of [KotlinRawPositionContext] around,
    // we will cache all of them  just in case.
    private val visibilityCheckerPerPositionContextCache = mutableMapOf<KotlinRawPositionContext, KaUseSiteVisibilityChecker>()

    /**
     * Returns true if the declaration or any of its parents have the [visibility] modifier.
     */
    private fun KtDeclaration.hasEffectiveVisibility(visibility: KtModifierKeywordToken): Boolean {
        return parentsWithSelf.any { it is KtModifierListOwner && it.hasModifier(visibility) }
    }

    fun canBeVisible(declaration: PsiElement): Boolean = forbidAnalysis("canBeVisible") {
        val originalFile = parameters.originalFile
        if (originalFile is KtCodeFragment) return true

        // TODO: We should consider only showing private members for invocationCount > 2
        if (!parameters.isRerun && parameters.invocationCount >= 2) return true

        val declarationContainingFile = declaration.containingFile ?: return false

        if (declarationContainingFile is KtFile && declaration is KtDeclaration) {
            if (declaration.hasEffectiveVisibility(KtTokens.PRIVATE_KEYWORD)
                && declarationContainingFile != originalFile
                && declarationContainingFile != parameters.completionFile
            ) {
                return false
            } else if (declaration.hasEffectiveVisibility(KtTokens.INTERNAL_KEYWORD)) {
                return canAccessInternalDeclarationsFromFile(declarationContainingFile)
            } else {
                return true
            }
        } else if (declaration is PsiClass) {
            return declaration.hasModifier(JvmModifier.PUBLIC) && declaration.containingClass?.hasModifier(JvmModifier.PUBLIC) != false
        } else if (declaration is PsiMember) {
            return declaration.hasModifier(JvmModifier.PUBLIC) && declaration.containingClass?.hasModifier(JvmModifier.PUBLIC) == true
        } else {
            return false
        }
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

    context(_: KaSession)
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

    context(_: KaSession)
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