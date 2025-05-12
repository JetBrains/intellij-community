// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

/**
 * A request to create Kotlin callable from the usage in Kotlin.
 */
internal class CreateMethodFromKotlinUsageRequest @OptIn(KaExperimentalApi::class) constructor(
    functionCall: KtCallExpression,
    modifiers: Collection<JvmModifier>,
    val receiverExpression: KtExpression?,
    receiverType: KaType?, // (in case receiverExpression is null) it can be notnull when there's implicit receiver: `blah { unknownFunc() }`
    val isExtension: Boolean,
    val isAbstractClassOrInterface: Boolean,
    val isForCompanion: Boolean,
) : CreateExecutableFromKotlinUsageRequest<KtCallExpression>(functionCall, modifiers), CreateMethodRequest {
    internal val targetClass: PsiElement? = initializeTargetClass(receiverExpression, functionCall)
    @OptIn(KaExperimentalApi::class)
    internal val receiverTypePointer: KaTypePointer<KaType>? = receiverType?.createPointer()

    private fun initializeTargetClass(receiverExpression: KtExpression?, functionCall: KtCallExpression): PsiElement? {
        return analyze(functionCall) {
            (receiverExpression?.resolveExpression() as? KaClassLikeSymbol)?.psi
        }
    }

    private val returnType:List<ExpectedType> = initializeReturnType(functionCall)

    private fun initializeReturnType(functionCall: KtCallExpression): List<ExpectedType> {
        return analyze(functionCall) {
            val returnJvmType = functionCall.getExpectedKotlinType() ?: return emptyList()
            listOf(returnJvmType)
        }
    }

    override fun isValid(): Boolean = super.isValid() && getReferenceName() != null

    override fun getMethodName(): @NlsSafe String = getReferenceName()!!

    override fun getReturnType(): List<ExpectedType> = returnType

    private fun getReferenceName(): String? = (call.calleeExpression as? KtSimpleNameExpression)?.getReferencedName()
}
