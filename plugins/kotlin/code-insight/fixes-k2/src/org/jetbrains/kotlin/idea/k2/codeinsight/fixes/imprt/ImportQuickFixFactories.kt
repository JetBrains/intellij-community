// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory

object ImportQuickFixFactories {
    val invisibleReferenceFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.InvisibleReference ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }

    /**
     * Used only for importing references on the fly. In all other cases import fixes for unresolved references are created
     * by [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.KotlinFirUnresolvedReferenceQuickFixProvider]
     */
    val unresolvedReferenceFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedReference ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }

    /**
     * See KDoc for [unresolvedReferenceFactory].
     */
    val unresolvedReferenceWrongReceiverFactory = KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.UnresolvedReferenceWrongReceiver ->
        ImportQuickFixProvider.getFixes(diagnostic)
    }
}