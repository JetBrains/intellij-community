// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveSupertypeFix
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object RemoveSupertypeFixFactory {

    val removeSupertypeFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ManyClassesInSupertypeList ->
        val superType = diagnostic.psi.getStrictParentOfType<KtSuperTypeListEntry>() ?: return@ModCommandBased emptyList()

        listOf(RemoveSupertypeFix(superType))
    }
}
