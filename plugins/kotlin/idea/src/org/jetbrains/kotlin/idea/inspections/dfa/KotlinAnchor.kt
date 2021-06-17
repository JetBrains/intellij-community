// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtWhenCondition

sealed class KotlinAnchor: DfaAnchor {
    /**
     * An anchor used to mark boolean condition on when branch (if it's true then branch is taken)
     */
    data class KotlinWhenConditionAnchor(val condition : KtWhenCondition): KotlinAnchor()

    data class KotlinExpressionAnchor(val expression : KtExpression): KotlinAnchor()
}