// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * A request to create Kotlin callable from the usage in Kotlin.
 */
internal class CreateMethodFromKotlinUsageRequest (
    private val functionCall: KtCallExpression,
    modifiers: Collection<JvmModifier>,
    val receiverExpression: KtExpression?,
    val receiverType: KtType?, // (in case receiverExpression is null) it can be notnull when there's implicit receiver: `blah { unknownFunc() }`
    val isExtension: Boolean,
    val isAbstractClassOrInterface: Boolean,
    val isForCompanion: Boolean
) : CreateExecutableFromKotlinUsageRequest<KtCallExpression>(functionCall, modifiers), CreateMethodRequest {
    private val returnType = mutableListOf<ExpectedType>()

    init {
      analyze(functionCall) {
          initializeReturnType()
      }
    }

    context (KtAnalysisSession)
    private fun initializeReturnType() {
        val returnJvmType = functionCall.getExpectedKotlinType() ?: return
        (returnJvmType.theType as? JvmReferenceType)?.let { if (it.resolve() == null) return }
        returnType.add(returnJvmType)
    }

    override fun isValid(): Boolean = super.isValid() && getReferenceName() != null

    override fun getMethodName(): @NlsSafe String = getReferenceName()!!

    override fun getReturnType(): List<ExpectedType> = returnType

    private fun getReferenceName(): String? = (call.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
}
