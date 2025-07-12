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
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.KaTypeRenderer
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.extensions.modifyRenderedType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.convertToJvmType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.types.Variance

/**
 * A request to create Kotlin property from the usage in Kotlin.
 */
internal class CreatePropertyFromKotlinUsageRequest (
    referenceExpression: KtNameReferenceExpression,
    private val modifiers: Collection<JvmModifier>,
    receiverType: KaType?,
    val isExtension: Boolean,
    val isConst: Boolean = false,
) : CreateFieldRequest {
    private val referencePointer = referenceExpression.createSmartPointer()
    private val returnType: List<ExpectedType> = initializeReturnType(referenceExpression)
    @OptIn(KaExperimentalApi::class)
    val receiverTypeString: String? = getReceiverTypeString(referenceExpression, receiverType, K2CreateFunctionFromUsageUtil.WITH_TYPE_NAMES_FOR_CREATE_ELEMENTS)

    @OptIn(KaExperimentalApi::class)
    val receiverTypeNameString: String? = getReceiverTypeString(referenceExpression, receiverType, KaTypeRendererForSource.WITH_SHORT_NAMES)

    private fun initializeReturnType(referenceExpression: KtNameReferenceExpression): List<ExpectedType> {
        return analyze(referenceExpression) {
            val returnJvmType = referenceExpression.getExpectedKotlinType()
            if (returnJvmType == null) {
                val expectedType = builtinTypes.any
                val jvmType = expectedType.convertToJvmType(referenceExpression) ?: return emptyList()
                return listOf(ExpectedKotlinType.create(expectedType, jvmType))
            }
            listOf(returnJvmType)
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun getReceiverTypeString(
        referenceExpression: KtNameReferenceExpression, receiverType: KaType?, renderer: KaTypeRenderer
    ): String? {
        return analyze(referenceExpression) {
            val renderedType = receiverType?.render(renderer, position = Variance.IN_VARIANCE) ?: return@analyze null
            modifyRenderedType(referenceExpression, renderedType)
        }
    }

    override fun isValid(): Boolean = referencePointer.element?.getReferencedName() != null

    override fun getFieldName(): String = referencePointer.element?.getReferencedName() ?: ""

    override fun getFieldType(): List<ExpectedType> = returnType

    override fun getTargetSubstitutor(): JvmSubstitutor = PsiJvmSubstitutor(referencePointer.project, PsiSubstitutor.EMPTY)

    override fun getInitializer(): JvmValue? = null

    override fun getAnnotations(): Collection<AnnotationRequest?> = emptyList()

    override fun getModifiers(): Collection<JvmModifier> = modifiers

    override fun isConstant(): Boolean = isConst
}
