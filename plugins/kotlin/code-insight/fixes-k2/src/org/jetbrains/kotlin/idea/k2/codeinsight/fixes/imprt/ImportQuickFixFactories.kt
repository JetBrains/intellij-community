// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory

/**
 * Note: generic unresolved references registration is handled
 * by [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.KotlinFirUnresolvedReferenceQuickFixProvider]
 */
object ImportQuickFixFactories {
    val invisibleReferenceFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.InvisibleReference ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }

    val unresolvedReferenceFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }

    val unresolvedReferenceWrongReceiverFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedReferenceWrongReceiver ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }
    
    val delegateSpecialFunctionMissingFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.DelegateSpecialFunctionMissing ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }

    val delegateSpecialFunctionNoneApplicableFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }
}