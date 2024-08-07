// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.k2.quickfixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaCompilerPluginDiagnostic0
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationBundle
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlinx.serialization.compiler.fir.checkers.FirSerializationErrors
import java.util.Collections.emptyList

internal object JsonFormatRedundantDefaultFixFactory {

    val replaceWithInstanceFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaCompilerPluginDiagnostic0 ->
        if (diagnostic.factoryName != FirSerializationErrors.JSON_FORMAT_REDUNDANT_DEFAULT.name) return@ModCommandBased emptyList()
        val element = diagnostic.psi as? KtCallExpression ?: return@ModCommandBased emptyList()

        listOf(
            JsonRedundantDefaultQuickFix(element)
        )
    }

    private class JsonRedundantDefaultQuickFix(
        element: KtCallExpression,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtCallExpression, Unit>(element, Unit) {

        override fun getFamilyName(): String = KotlinSerializationBundle.message("replace.with.default.json.format")

        override fun invoke(
            actionContext: ActionContext,
            element: KtCallExpression,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            val callee = element.calleeExpression ?: return
            element.replace(callee)
        }
    }
}
