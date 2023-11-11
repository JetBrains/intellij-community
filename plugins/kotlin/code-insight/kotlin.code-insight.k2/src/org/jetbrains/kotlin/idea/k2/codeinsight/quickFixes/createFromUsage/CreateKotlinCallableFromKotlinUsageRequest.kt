// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal class CreateKotlinCallableFromKotlinUsageRequest (
    functionCall: KtCallExpression,
    modifiers: Collection<JvmModifier>
) : CreateExecutableFromKotlinUsageRequest<KtCallExpression>(functionCall, modifiers), CreateMethodRequest {

    override fun isValid(): Boolean = super.isValid() && getReferenceName() != null

    override fun getMethodName(): @NlsSafe String = getReferenceName()!!

    override fun getReturnType(): List<ExpectedType> = emptyList() //todo guess return type

    private fun getReferenceName(): String? = (call.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
}
