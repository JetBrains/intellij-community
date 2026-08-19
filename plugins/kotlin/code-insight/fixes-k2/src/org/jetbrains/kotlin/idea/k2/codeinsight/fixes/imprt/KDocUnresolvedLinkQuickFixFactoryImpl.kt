// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.codeinsight.utils.KDocUnresolvedLinkQuickFixFactory
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.Name

internal class KDocUnresolvedLinkQuickFixFactoryImpl : KDocUnresolvedLinkQuickFixFactory {
    override fun createQuickFix(element: PsiElement): IntentionAction? {
        val kDocName = element as? KDocName ?: return null

        return analyze(kDocName) {
            val importContext =
                DefaultImportContext(kDocName, ImportPositionTypeAndReceiver.KDocNameReference(kDocName.getQualifier()))
            val indexProvider =
                KtSymbolFromIndexProvider(kDocName.containingKtFile)

            val candidates = listOf(
                CallableImportCandidatesProvider(importContext, allowInapplicableExtensions = true),
                ClassifierImportCandidatesProvider(importContext),
            ).flatMap { it.collectCandidates(Name.identifier(kDocName.getNameText()), indexProvider) }

            val importData = ImportQuickFixProvider.createImportData(kDocName, candidates) ?: return null
            val variants = importData.importVariants.ifEmpty { return null }
            KDocUnresolvedLinkQuickFix(kDocName, variants)
        }
    }
}