// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.MoveWhenElseBranchFix
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

internal object ElseMisplacedInWhenFixFactory {

    val moveWhenElseBranch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ElseMisplacedInWhen ->
        val whenExpression = diagnostic.psi.getNonStrictParentOfType<KtWhenExpression>() ?: return@ModCommandBased emptyList()

        listOfNotNull(
            MoveWhenElseBranchFix.createIfApplicable(whenExpression)
        )
    }
}