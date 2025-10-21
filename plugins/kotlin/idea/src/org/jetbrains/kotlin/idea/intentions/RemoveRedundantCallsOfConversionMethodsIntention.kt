// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.types.isFlexible

@Suppress("DEPRECATION")
class RemoveRedundantCallsOfConversionMethodsInspection : IntentionBasedInspection<KtQualifiedExpression>(
    RemoveRedundantCallsOfConversionMethodsIntention::class
)

class RemoveRedundantCallsOfConversionMethodsIntention : SelfTargetingRangeIntention<KtQualifiedExpression>(
    KtQualifiedExpression::class.java,
    KotlinBundle.messagePointer("remove.redundant.calls.of.the.conversion.method")
) {

    @ExperimentalUnsignedTypes
    @delegate:SafeFieldForPreview
    private val targetClassMap: Map<String, String?> by lazy {
        mapOf(
            "toString()" to String::class.qualifiedName,
            "toDouble()" to Double::class.qualifiedName,
            "toFloat()" to Float::class.qualifiedName,
            "toLong()" to Long::class.qualifiedName,
            "toInt()" to Int::class.qualifiedName,
            "toChar()" to Char::class.qualifiedName,
            "toShort()" to Short::class.qualifiedName,
            "toByte()" to Byte::class.qualifiedName,
            "toULong()" to ULong::class.qualifiedName,
            "toUInt()" to UInt::class.qualifiedName,
            "toUShort()" to UShort::class.qualifiedName,
            "toUByte()" to UByte::class.qualifiedName
        )
    }


    override fun applyTo(element: KtQualifiedExpression, editor: Editor?) {
        element.replaced(element.receiverExpression)
    }

    override fun applicabilityRange(element: KtQualifiedExpression): TextRange? {
        val selectorExpression = element.selectorExpression ?: return null
        val selectorExpressionText = selectorExpression.text
        val qualifiedName = targetClassMap[selectorExpressionText] ?: return null
        return if (element.receiverExpression.isApplicableReceiverExpression(qualifiedName)) {
            selectorExpression.textRange
        } else {
            null
        }
    }

    private fun KotlinType.getFqNameAsString(): String? = constructor.declarationDescriptor?.let {
        DescriptorUtils.getFqName(it).asString()
    }

    private fun KtExpression.isApplicableReceiverExpression(qualifiedName: String): Boolean = when (this) {
        is KtStringTemplateExpression -> String::class.qualifiedName
        is KtConstantExpression -> getType(analyze())?.getFqNameAsString()
        else -> {
            val resolvedCall = resolveToCall()
            if ((resolvedCall?.call?.callElement as? KtBinaryExpression)?.operationToken in OperatorConventions.COMPARISON_OPERATIONS) {
                // Special case here because compareTo returns Int
                Boolean::class.qualifiedName
            } else {
                resolvedCall?.candidateDescriptor?.returnType?.let {
                    when {
                        it.isFlexible() -> null
                        parent !is KtSafeQualifiedExpression && (this is KtSafeQualifiedExpression || it.isMarkedNullable) -> null
                        else -> it.getFqNameAsString()
                    }
                }
            }
        }
    } == qualifiedName
}
