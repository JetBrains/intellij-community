// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase
import org.jetbrains.kotlin.psi.KtExpression

class RedundantIfInspection : RedundantIfInspectionBase() {
    override fun isBooleanExpression(expression: KtExpression): Boolean = analyze(expression) {
        expression.getKtType()?.isBoolean == true
    }
}