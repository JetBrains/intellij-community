// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtCallableReferenceExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS
import java.io.Serializable

data class KotlinReferenceData(
    var startOffset: Int,
    var endOffset: Int,
    val fqName: String,
    val isQualifiable: Boolean,
    val kind: Kind
) : Cloneable, Serializable {

    enum class Kind {
        CLASS,
        PACKAGE,
        FUNCTION,
        PROPERTY;

        companion object {
            fun fromDescriptor(descriptor: DeclarationDescriptor) = when (descriptor.getImportableDescriptor()) {
                is ClassDescriptor -> CLASS
                is PackageViewDescriptor -> PACKAGE
                is FunctionDescriptor -> FUNCTION
                is PropertyDescriptor -> PROPERTY
                else -> null
            }
        }
    }

    public override fun clone(): KotlinReferenceData {
        try {
            return super.clone() as KotlinReferenceData
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException()
        }
    }

    companion object {
        fun isQualifiable(refElement: KtElement, descriptor: DeclarationDescriptor): Boolean {
            refElement.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference }?.let {
                val receiverExpression = it.receiverExpression

                if (receiverExpression != null) {
                    val lhs = it.analyze(BodyResolveMode.PARTIAL)[BindingContext.DOUBLE_COLON_LHS, receiverExpression]
                    if (lhs is DoubleColonLHS.Expression) return false
                }
                return descriptor.containingDeclaration is ClassifierDescriptor
            }

            return !descriptor.isExtension
        }
    }
}