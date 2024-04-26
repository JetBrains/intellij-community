// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.types.JvmReferenceType
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFromUsageUtil.resolveExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * A request to create Kotlin callable from the usage in Kotlin.
 */
internal class CreateMethodFromKotlinUsageRequest (
    functionCall: KtCallExpression,
    modifiers: Collection<JvmModifier>,
    val receiverExpression: KtExpression?,
    val receiverType: KtType?, // (in case receiverExpression is null) it can be notnull when there's implicit receiver: `blah { unknownFunc() }`
    val isExtension: Boolean,
    val isAbstractClassOrInterface: Boolean,
    val isForCompanion: Boolean,
) : CreateExecutableFromKotlinUsageRequest<KtCallExpression>(functionCall, modifiers), CreateMethodRequest {
    internal val targetClass: PsiElement? = initializeTargetClass(receiverExpression, functionCall)

    private fun initializeTargetClass(receiverExpression: KtExpression?, functionCall: KtCallExpression): PsiElement? {
        return analyze(functionCall) {
            (receiverExpression?.resolveExpression() as? KtClassLikeSymbol)?.psi
        }
    }

    private val returnType:List<ExpectedType> = initializeReturnType(functionCall)

    private fun initializeReturnType(functionCall: KtCallExpression): List<ExpectedType> {
        return analyze(functionCall) {
            val returnJvmType = functionCall.getExpectedKotlinType() ?: return emptyList()
            if (returnJvmType !is ExpectedKotlinType) {
                (returnJvmType.theType as? JvmReferenceType)?.let { if (it.resolve() == null) return emptyList() }
            }
            listOf(returnJvmType)
        }
    }

    override fun isValid(): Boolean = super.isValid() && getReferenceName() != null

    override fun getMethodName(): @NlsSafe String = getReferenceName()!!

    override fun getReturnType(): List<ExpectedType> = returnType

    private fun getReferenceName(): String? = (call.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
}
