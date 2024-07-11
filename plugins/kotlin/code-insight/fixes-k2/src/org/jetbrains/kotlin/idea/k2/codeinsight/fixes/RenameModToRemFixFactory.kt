// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RenameModToRemFix
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.types.expressions.OperatorConventions.REM_TO_MOD_OPERATION_NAMES

internal object RenameModToRemFixFactory {

    val deprecatedBinaryModFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.DeprecatedBinaryMod ->
        createQuickFix(diagnostic.psi)
    }

    val forbiddenBinaryModFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ForbiddenBinaryMod ->
        createQuickFix(diagnostic.psi)
    }

    private fun createQuickFix(element: PsiElement): List<RenameModToRemFix> {
        val operatorMod = element.getNonStrictParentOfType<KtNamedFunction>() ?: return emptyList()
        val newName = operatorMod.nameAsName?.let { REM_TO_MOD_OPERATION_NAMES.inverse()[it] } ?: return emptyList()

        return listOf(
            RenameModToRemFix(operatorMod, newName)
        )
    }
}
