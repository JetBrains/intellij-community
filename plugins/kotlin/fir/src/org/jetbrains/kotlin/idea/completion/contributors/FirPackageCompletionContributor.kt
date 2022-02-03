// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.contributors

import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.FirNameReferencePositionContext
import org.jetbrains.kotlin.idea.completion.context.FirRawPositionCompletionContext
import org.jetbrains.kotlin.idea.frontend.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.frontend.api.symbols.KtPackageSymbol
import org.jetbrains.kotlin.idea.isExcludedFromAutoImport
import org.jetbrains.kotlin.idea.project.languageVersionSettings

internal class FirPackageCompletionContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<FirRawPositionCompletionContext>(basicContext, priority) {

    override fun KtAnalysisSession.complete(positionContext: FirRawPositionCompletionContext) {
        val rootSymbol = if (positionContext !is FirNameReferencePositionContext || positionContext.explicitReceiver == null) {
            ROOT_PACKAGE_SYMBOL
        } else {
            positionContext.explicitReceiver?.reference()?.resolveToSymbol() as? KtPackageSymbol
        } ?: return
        rootSymbol.getPackageScope()
            .getPackageSymbols(scopeNameFilter)
            .filterNot { packageName ->
                packageName.fqName.isExcludedFromAutoImport(project, originalKtFile, originalKtFile.languageVersionSettings)
            }
            .forEach { packageSymbol ->
                sink.addElement(lookupElementFactory.createPackagePartLookupElement(packageSymbol.fqName))
            }
    }
}