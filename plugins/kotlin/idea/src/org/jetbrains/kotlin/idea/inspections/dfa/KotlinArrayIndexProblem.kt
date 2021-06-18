// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtExpression

class KotlinArrayIndexProblem(val arrayAccess: KtArrayAccessExpression, val index: KtExpression): UnsatisfiedConditionProblem