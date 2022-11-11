// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.value.RelationType
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType
import org.jetbrains.kotlin.idea.util.RangeKtExpressionType.*

fun RangeKtExpressionType.getRelationType() =
    when (this) {
        RANGE_TO -> RelationType.GE to RelationType.LE
        RANGE_UNTIL, UNTIL -> RelationType.GE to RelationType.LT
        DOWN_TO -> RelationType.LE to RelationType.GE
    }
