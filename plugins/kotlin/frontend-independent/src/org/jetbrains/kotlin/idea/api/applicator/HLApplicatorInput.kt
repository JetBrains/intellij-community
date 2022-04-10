// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.api.applicator

import com.intellij.psi.PsiElement

/**
 * Data which [HLApplicator] is needed to perform the fix
 *
 * Created by [HLApplicatorInputProvider] or via [org.jetbrains.kotlin.idea.fir.api.fixes.HLDiagnosticFixFactory]
 *
 * Should not store inside
 * - Everything that came from [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] like :
 *      - [org.jetbrains.kotlin.analysis.api.symbols.KtSymbol] consider using [org.jetbrains.kotlin.analysis.api.symbols.pointers.KtSymbolPointer] instead
 *      - [org.jetbrains.kotlin.analysis.api.types.KtType]
 *      - [org.jetbrains.kotlin.analysis.api.calls.KtCall]
 * - [org.jetbrains.kotlin.analysis.api.KtAnalysisSession] instance itself
 * - [PsiElement] consider using [com.intellij.psi.SmartPsiElementPointer] instead
 *
 */
interface HLApplicatorInput {
    fun isValidFor(psi: PsiElement): Boolean = true

    companion object Empty : HLApplicatorInput
}
