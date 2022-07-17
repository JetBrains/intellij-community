// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.applicators

import com.intellij.psi.PsiElement

/**
 * Data which [KotlinApplicator] is needed to perform the fix
 *
 * Created by [KotlinApplicatorInputProvider] or via [org.jetbrains.kotlin.idea.codeinsight.api.fixes.KotlinDiagnosticFixFactory]
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
interface KotlinApplicatorInput{
    fun isValidFor(psi: PsiElement): Boolean = true

    companion object Empty : KotlinApplicatorInput
}
