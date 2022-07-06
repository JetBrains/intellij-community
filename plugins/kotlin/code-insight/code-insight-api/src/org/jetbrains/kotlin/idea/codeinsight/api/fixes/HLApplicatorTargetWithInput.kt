// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsight.api.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.codeinsight.api.HLApplicatorInput

class HLApplicatorTargetWithInput<PSI : PsiElement, INPUT : HLApplicatorInput>(
    val target: PSI,
    val input: INPUT,
) {
    operator fun component1() = target
    operator fun component2() = input
}

@Suppress("NOTHING_TO_INLINE")
inline infix fun <PSI : PsiElement, INPUT : HLApplicatorInput> PSI.withInput(input: INPUT) =
    HLApplicatorTargetWithInput(this, input)