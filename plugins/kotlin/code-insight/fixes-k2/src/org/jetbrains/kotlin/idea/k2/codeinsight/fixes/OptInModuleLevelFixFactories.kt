// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.base.util.names.FqNames
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.OptInFixUtils.annotationApplicable
import org.jetbrains.kotlin.idea.quickfix.AddModuleOptInFix
import org.jetbrains.kotlin.resolve.checkers.OptInNames

internal object OptInModuleLevelFixFactories {

    val optInIsNotEnabledFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.OptInIsNotEnabled ->
        val file = diagnostic.psi.containingKtFile
        val module = file.module
            ?: return@IntentionBased emptyList()

        val annotationFqName = OptInNames.REQUIRES_OPT_IN_FQ_NAME.takeIf { it.annotationApplicable() }
            ?: FqNames.OptInFqNames.OLD_EXPERIMENTAL_FQ_NAME.takeIf { it.annotationApplicable() }
            ?: return@IntentionBased emptyList()

        val quickFix = AddModuleOptInFix(
            file = file,
            module = module,
            annotationFqName = annotationFqName,
        )
        listOf(quickFix)
    }
}