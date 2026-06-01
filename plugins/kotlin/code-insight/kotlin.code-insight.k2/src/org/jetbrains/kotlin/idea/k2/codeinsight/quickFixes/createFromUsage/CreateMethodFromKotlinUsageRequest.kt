// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypePointer
import org.jetbrains.kotlin.idea.codeinsight.utils.resolveExpression
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.computeCallTypeParameterInfo
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.computeExpectedParams
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression

/**
 * A request to create Kotlin callable from the usage in Kotlin.
 */
internal class CreateMethodFromKotlinUsageRequest private constructor(
    functionCall: KtElement,
    private val referenceName: String,
    expectedParameters: List<ExpectedParameter>,
    modifiers: Collection<JvmModifier>,
    private val returnType: List<ExpectedType>,
    val receiverExpression: KtExpression?,
    receiverType: KaType?, // (in case receiverExpression is null) it can be notnull when there's implicit receiver: `blah { unknownFunc() }`
    val isExtension: Boolean,
    val isAbstractClassOrInterface: Boolean,
    val isForCompanion: Boolean,
    val operatorFunction: Boolean,
    val callTypeParameterInfo: CallTypeParameterInfo = CallTypeParameterInfo.EMPTY,
) : CreateExecutableFromKotlinUsageRequest<KtElement>(functionCall, expectedParameters, modifiers), CreateMethodRequest {

    internal val targetClass: PsiElement? = initializeTargetClass(receiverExpression, functionCall)
    @OptIn(KaExperimentalApi::class)
    internal val receiverTypePointer: KaTypePointer<KaType>? = receiverType?.createPointer()

    private fun initializeTargetClass(receiverExpression: KtExpression?, functionCall: KtElement): PsiElement? {
        return analyze(functionCall) {
            (receiverExpression?.resolveExpression() as? KaClassLikeSymbol)?.psi
        }
    }

    override fun isValid(): Boolean = super.isValid() && referenceName.isNotEmpty()

    override fun getMethodName(): @NlsSafe String = referenceName

    override fun getReturnType(): List<ExpectedType> = returnType
    companion object {
        internal fun createMethodRequest(
            functionCall: KtElement,
            modifiers: Collection<JvmModifier>,
            referenceName: String,
            receiverExpression: KtExpression?,
            receiverType: KaType?,
            isExtension: Boolean,
            isAbstractClassOrInterface: Boolean,
            isForCompanion: Boolean,
            operatorFunction: Boolean,
            targetContainerClass: KtClassOrObject? = null,
        ): CreateMethodFromKotlinUsageRequest = analyze(functionCall) {
            when (functionCall) {
                is KtCallExpression -> CreateMethodFromKotlinUsageRequest(
                    functionCall = functionCall,
                    referenceName = referenceName,
                    expectedParameters = computeExpectedParams(functionCall),
                    modifiers = modifiers,
                    returnType = functionCall.getExpectedKotlinType()?.let(::listOf) ?: emptyList(),
                    receiverExpression = receiverExpression,
                    receiverType = receiverType,
                    isExtension = isExtension,
                    isAbstractClassOrInterface = isAbstractClassOrInterface,
                    isForCompanion = isForCompanion,
                    operatorFunction = operatorFunction,
                    callTypeParameterInfo = computeCallTypeParameterInfo(functionCall, targetContainerClass),
                )

                is KtBinaryExpression ->
                    CreateMethodFromKotlinUsageRequest(
                        functionCall = functionCall,
                        referenceName = referenceName,
                        expectedParameters = computeExpectedParamsForBinaryExpression(functionCall),
                        modifiers = modifiers,
                        returnType = createReturnTypeForBinaryExpression(functionCall),
                        receiverExpression = receiverExpression,
                        receiverType = receiverType,
                        isExtension = isExtension,
                        isAbstractClassOrInterface = isAbstractClassOrInterface,
                        isForCompanion = isForCompanion,
                        operatorFunction = operatorFunction,
                    )

                else -> error("Unsupported function call: $functionCall")
            }
        }
    }
}