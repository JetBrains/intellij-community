// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.k2.quickfixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaCompilerPluginDiagnostic0
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationBundle
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlinx.serialization.compiler.fir.checkers.FirSerializationErrors
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerializationAnnotations

internal object AddKotlinxSerializationTransientImportQuickFixFactory {

    val addKotlinxSerializationTransientImportQuickFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaCompilerPluginDiagnostic0 ->
        if (diagnostic.factoryName != FirSerializationErrors.INCORRECT_TRANSIENT.name) return@ModCommandBased emptyList()

        listOf(
            AddKotlinxSerializationTransientImportQuickFix(diagnostic.psi)
        )
    }

    private class AddKotlinxSerializationTransientImportQuickFix(
        element: PsiElement
    ) : KotlinPsiUpdateModCommandAction.ElementBased<PsiElement, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: PsiElement,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            (element.containingFile as? KtFile)?.addImport(
                fqName = SerializationAnnotations.serialTransientFqName,
                allUnder = false,
                alias = null,
                project = actionContext.project,
            )
        }

        override fun getFamilyName(): String = KotlinSerializationBundle.message("intention.name.import", SerializationAnnotations.serialTransientFqName)
    }
}
