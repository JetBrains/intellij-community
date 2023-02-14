// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsights.impl.base.ConvertToConcatenatedStringIntentionBase
import org.jetbrains.kotlin.psi.KtExpression

class ConvertToConcatenatedStringIntention : ConvertToConcatenatedStringIntentionBase() {
    override fun isExpressionOfStringType(expression: KtExpression): Boolean = KotlinBuiltIns.isString(expression.analyze().getType(expression))
}