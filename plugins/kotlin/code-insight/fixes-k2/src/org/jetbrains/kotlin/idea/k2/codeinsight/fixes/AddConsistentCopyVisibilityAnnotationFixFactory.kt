// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.util.parents
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AddAnnotationFix
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal object AddConsistentCopyVisibilityAnnotationFixFactories {

    val errorFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.DataClassCopyVisibilityWillBeChangedError ->
        listOfNotNull(createQuickFix(diagnostic.psi))
    }

    val warningFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.DataClassCopyVisibilityWillBeChangedWarning ->
        listOfNotNull(createQuickFix(diagnostic.psi))
    }

    private fun createQuickFix(element: KtPrimaryConstructor): AddAnnotationFix? {
        val containingClass = element.parents(withSelf = true).firstIsInstanceOrNull<KtClass>() ?: return null
        return AddAnnotationFix(containingClass, StandardClassIds.Annotations.ConsistentCopyVisibility)
    }
}
