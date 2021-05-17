package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor
import org.jetbrains.kotlin.psi.KtExpression

data class KotlinExpressionAnchor(val expression : KtExpression): DfaAnchor