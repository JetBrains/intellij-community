// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.ConvertToConcatenatedStringIntentionBase
import org.jetbrains.kotlin.psi.KtExpression

class ConvertToConcatenatedStringIntention : ConvertToConcatenatedStringIntentionBase() {
    override fun isExpressionOfStringType(expression: KtExpression): Boolean = analyze(expression) {
        expression.getKtType()?.isString == true
    }
}