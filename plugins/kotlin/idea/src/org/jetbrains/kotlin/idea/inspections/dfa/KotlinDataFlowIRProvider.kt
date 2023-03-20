// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.dfa

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtExpression

class KotlinDataFlowIRProvider : DataFlowIRProvider {
    override fun createControlFlow(factory: DfaValueFactory, psiBlock: PsiElement): ControlFlow? {
        if (psiBlock !is KtExpression) return null
        return KtControlFlowBuilder(factory, psiBlock).buildFlow()
    }
}