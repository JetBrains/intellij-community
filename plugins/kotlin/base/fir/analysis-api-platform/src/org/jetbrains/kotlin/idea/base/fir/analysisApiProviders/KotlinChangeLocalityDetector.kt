// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.codeInsight.daemon.ChangeLocalityDetector
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.file.structure.LLFirDeclarationModificationService

internal class KotlinChangeLocalityDetector : ChangeLocalityDetector {
    override fun getChangeHighlightingDirtyScopeFor(changedElement: PsiElement): PsiElement? {
        // we shouldn't process comments here because the default detector will do that for us

        @OptIn(LLFirInternals::class)
        return LLFirDeclarationModificationService.getInstance(changedElement.project).elementToRehighlight(changedElement)
    }
}
