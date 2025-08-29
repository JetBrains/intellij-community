// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ModCommandAction
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal object ChangeSuspendInHierarchyFixFactory {
    val suspendOverriddenByNonSuspend = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.SuspendOverriddenByNonSuspend ->
        createFixes(diagnostic)
    }

    val nonSuspendOverriddenBySuspend = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NonSuspendOverriddenBySuspend -> 
        createFixes(diagnostic) 
    }
    
    private fun createFixes(diagnostic: KaFirDiagnostic<KtCallableDeclaration>): List<ModCommandAction> {
        val function = diagnostic.psi as? KtNamedFunction ?: return emptyList()

        return listOf(
            ChangeSuspendInHierarchyFix(function, addModifier = true),
            ChangeSuspendInHierarchyFix(function, addModifier = false),
        )
    }
}
