// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtWhenCondition
import org.jetbrains.kotlin.psi.KtWhenEntryGuard

sealed class KotlinAnchor: DfaAnchor {
    /**
     * An anchor used to mark the boolean condition of a when branch.
     * It is used to evaluate whether a branch is always or never taken, in conjunction with the [whenGuardAnchor] if it is present.
     * In isolation, a branch is always taken if the condition is always true _and_ the guard is always true or does not exist.
     * Conversely, a branch is never taken if the condition is always false _or_ the guard, if it exists, is always false.
     * Also note that if a preceding branch is always taken, then the following branches are never taken.
     */
    data class KotlinWhenConditionAnchor(val condition : KtWhenCondition, val whenGuardAnchor: KotlinWhenGuardAnchor?): KotlinAnchor()

    /**
     * An anchor used to mark the boolean guard expression of a when branch.
     * See [KotlinWhenConditionAnchor].
     */
    data class KotlinWhenGuardAnchor(val whenGuard : KtWhenEntryGuard): KotlinAnchor()

    data class KotlinExpressionAnchor(val expression : KtExpression): KotlinAnchor()

    /**
     * An anchor used to mark boolean condition that tells whether for-expression is ever visited or not.
     * If its value is always false then for-expression is never visited
     */
    data class KotlinForVisitedAnchor(val forExpression : KtForExpression): KotlinAnchor()
}