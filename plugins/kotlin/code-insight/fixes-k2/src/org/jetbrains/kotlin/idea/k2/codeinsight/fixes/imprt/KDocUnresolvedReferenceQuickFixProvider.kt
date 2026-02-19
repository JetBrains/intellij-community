// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.references.KDocReference
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile


internal class KDocUnresolvedReferenceQuickFixProvider : UnresolvedReferenceQuickFixProvider<KDocReference>() {
    override fun getReferenceClass(): Class<KDocReference> = KDocReference::class.java

    override fun registerFixes(ref: KDocReference, registrar: QuickFixActionRegistrar) {
        val kDocName = ref.element
        val fix = createQuickFix(kDocName) ?: return
        registrar.register(fix)
    }

    private fun createQuickFix(kDocName: KDocName): KDocUnresolvedReferenceQuickFix? {
        return analyze(kDocName) {
            val importContext = DefaultImportContext(kDocName, ImportPositionTypeAndReceiver.KDocNameReference(kDocName.getQualifier()))
            val indexProvider = KtSymbolFromIndexProvider(kDocName.containingKtFile)

            val candidates = listOf(
                CallableImportCandidatesProvider(importContext, allowInapplicableExtensions = true),
                ClassifierImportCandidatesProvider(importContext),
            ).flatMap { it.collectCandidates(Name.identifier(kDocName.getNameText()), indexProvider) }

            val importData = ImportQuickFixProvider.createImportData(kDocName, candidates) ?: return null
            val variants = importData.importVariants.ifEmpty { return null }
            KDocUnresolvedReferenceQuickFix(kDocName, variants)
        }
    }
}

private class KDocUnresolvedReferenceQuickFix(
    element: KDocName,
    importVariants: List<AutoImportVariant>,
) : ImportLikeQuickFix(element, importVariants) {
    override fun getText(): @IntentionName String {
        return familyName
    }

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("add.qualifier")
    }

    override fun createAutoImportAction(
        editor: Editor,
        file: KtFile,
        filterSuggestions: (Collection<FqName>) -> Collection<FqName>
    ): QuestionAction? {
        return null
    }

    override fun fix(
        importVariant: AutoImportVariant,
        file: KtFile,
        project: Project
    ) {
        val kDocName = element as? KDocName ?: return

        project.executeWriteCommand(KotlinBundle.message("add.qualifier.command")) {
            val factory = KDocElementFactory(project)
            val qualifiedName = factory.createQualifiedKDocName(importVariant.fqName.asString())
            kDocName.replace(qualifiedName)
        }
    }
}