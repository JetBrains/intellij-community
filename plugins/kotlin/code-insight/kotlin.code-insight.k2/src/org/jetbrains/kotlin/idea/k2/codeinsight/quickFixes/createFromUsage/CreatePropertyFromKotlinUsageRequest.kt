// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmValue
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * A request to create Kotlin property from the usage in Kotlin.
 */
internal class CreatePropertyFromKotlinUsageRequest (
    referenceExpression: KtNameReferenceExpression,
    private val modifiers: Collection<JvmModifier>,
    val isExtension: Boolean
) : CreateFieldRequest {
    private val referencePointer = referenceExpression.createSmartPointer()

    private val returnType: List<ExpectedType> = initializeReturnType(referenceExpression)

    private fun initializeReturnType(referenceExpression: KtNameReferenceExpression): List<ExpectedType> {
        return analyze(referenceExpression) {
            val returnJvmType = referenceExpression.getExpectedKotlinType() ?: return emptyList()
            listOf(returnJvmType)
        }
    }

    override fun isValid(): Boolean = referencePointer.element?.getReferencedName() != null

    override fun getFieldName(): String = referencePointer.element?.getReferencedName() ?: ""

    override fun getFieldType(): List<ExpectedType> = returnType

    override fun getTargetSubstitutor(): JvmSubstitutor = PsiJvmSubstitutor(referencePointer.project, PsiSubstitutor.EMPTY)

    override fun getInitializer(): JvmValue? = null

    override fun getAnnotations(): Collection<AnnotationRequest?> = emptyList()

    override fun getModifiers(): Collection<JvmModifier> = modifiers

    override fun isConstant(): Boolean = false
}
