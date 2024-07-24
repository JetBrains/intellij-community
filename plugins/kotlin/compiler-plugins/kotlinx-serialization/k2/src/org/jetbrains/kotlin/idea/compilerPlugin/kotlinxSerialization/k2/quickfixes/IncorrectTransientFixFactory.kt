// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.k2.quickfixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KaIdeApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaCompilerPluginDiagnostic0
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationBundle
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlinx.serialization.compiler.fir.checkers.FirSerializationErrors
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerializationAnnotations

internal object IncorrectTransientFixFactory {

    val useKotlinxSerializationTransientFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaCompilerPluginDiagnostic0 ->
        if (diagnostic.factoryName != FirSerializationErrors.INCORRECT_TRANSIENT.name) return@ModCommandBased emptyList()
        val annotationEntry = diagnostic.psi as? KtAnnotationEntry ?: return@ModCommandBased emptyList()
        val isImportNeeded = annotationEntry.containingKtFile.importDirectives.find {
            it.importedFqName == SerializationAnnotations.serialTransientFqName
        } == null

        listOf(
            UseKotlinxSerializationTransientQuickFix(annotationEntry, isImportNeeded)
        )
    }

    private class UseKotlinxSerializationTransientQuickFix(
        element: KtAnnotationEntry,
        val isImportNeeded: Boolean,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtAnnotationEntry, Unit>(element, Unit) {

        @OptIn(KaIdeApi::class)
        override fun invoke(
            actionContext: ActionContext,
            element: KtAnnotationEntry,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            val psiFactory = KtPsiFactory(actionContext.project)
            val newAnnotationEntry = psiFactory.createAnnotationEntry("@${SerializationAnnotations.serialTransientFqName}")
            val replaced = element.replace(newAnnotationEntry) as KtAnnotationEntry
            shortenReferences(replaced)
        }

        override fun getFamilyName(): String {
            return if (isImportNeeded)
                KotlinSerializationBundle.message("intention.name.import", SerializationAnnotations.serialTransientFqName)
            else
                KotlinBundle.message("replace.with.0", SerializationAnnotations.serialTransientFqName)
        }
    }
}
