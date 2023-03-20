// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem
import com.intellij.codeInspection.dataFlow.value.DerivedVariableDescriptor
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression

sealed class KotlinProblem: UnsatisfiedConditionProblem {
    data class KotlinCastProblem(val operand: KtExpression, val cast: KtBinaryExpressionWithTypeRHS): KotlinProblem()
    data class KotlinArrayIndexProblem(private val lengthDescriptor: DerivedVariableDescriptor, val index: KtExpression):
        KotlinProblem(), IndexOutOfBoundsProblem {
        override fun getLengthDescriptor(): DerivedVariableDescriptor = lengthDescriptor
    }
    data class KotlinNullCheckProblem(val expr: KtPostfixExpression): KotlinProblem()
}