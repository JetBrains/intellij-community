// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.completion.impl.k2.context

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.completion.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.ImportStrategyDetector
import org.jetbrains.kotlin.idea.completion.impl.k2.LookupElementSink
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiUtil

internal class FirBasicCompletionContext(
    val parameters: CompletionParameters,
    val sink: LookupElementSink,
    val prefixMatcher: PrefixMatcher,
    val originalKtFile: KtFile,
    val fakeKtFile: KtFile,
    val targetPlatform: TargetPlatform,
    val symbolFromIndexProvider: KtSymbolFromIndexProvider,
) {

    val project: Project
        get() = originalKtFile.project

    val importStrategyDetector = ImportStrategyDetector(originalKtFile, project)

    companion object {

        fun createFromParameters(
            firParameters: KotlinFirCompletionParameters,
            result: CompletionResultSet,
        ): FirBasicCompletionContext? {
            val parameters = firParameters.ijParameters
            val originalKtFile = parameters.originalFile as? KtFile ?: return null
            val fakeKtFile = parameters.position.containingFile as? KtFile ?: return null
            val useSiteKtElement = parameters.position.parentOfType<KtElement>(withSelf = true) ?: return null

            return FirBasicCompletionContext(
                parameters = parameters,
                sink = LookupElementSink(result, firParameters),
                prefixMatcher = result.prefixMatcher,
                originalKtFile = originalKtFile,
                fakeKtFile = fakeKtFile,
                targetPlatform = originalKtFile.platform,
                symbolFromIndexProvider = KtSymbolFromIndexProvider.createForElement(useSiteKtElement),
            )
        }
    }
}

/**
 * Returns the same declaration from the [originalKtFile], or [declaration] if the original declaration is not found.
 *
 * To perform auto-completion, a slightly modified copy of the current file is created.
 * In particular, some extra PsiElement, such as an 'IntellijRulezzz' identifier, is inserted to fix the incomplete syntax tree.
 *
 * The copy is analyzed in a special mode that prefers declarations from the original file for performance reasons. Because of that,
 * the collected candidates also come from the original module (instead of the [KaDanglingFileModule] of the file copy).
 * However, PSI-based checks performed on the copy might break, as source elements for [KtSymbol]s point to original declarations.
 * For such checks, use this function to get a corresponding declaration from the original module.
 *
 * Local declarations are always analyzed in the file copy context, so they are returned as is.
 */
internal fun <T : KtDeclaration> getOriginalDeclarationOrSelf(declaration: T, originalKtFile: KtFile): T {
    val isLocal = when (declaration) {
        is KtParameter -> {
            // Parameters don't have their own designation, and they are re-analyzed with their owning function.
            true
        }
        else -> KtPsiUtil.isLocal(declaration)
    }

    if (isLocal) {
        // Local declarations are always analyzed in the dangling file context
        return declaration
    }

    return getOriginalElementOfSelf(declaration, originalKtFile)
}

/**
 * Returns the same [KtElement] from the [originalKtFile], or [element] if the original element is not found.
 *
 * On contrary with [getOriginalDeclarationOrSelf], [getOriginalElementOfSelf] returns all kinds of elements,
 * including those in local declarations.
 */
internal fun <T : KtElement> getOriginalElementOfSelf(element: T, originalKtFile: KtFile): T {
    return try {
        PsiTreeUtil.findSameElementInCopy(element, originalKtFile)
    } catch (_: IllegalStateException) {
        element
    }
}