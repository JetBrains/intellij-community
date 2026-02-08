// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.findUsages

import com.intellij.psi.PsiPackage
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.base.searching.usages.KotlinUsageTypeProvider
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.ANNOTATION
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.CLASS_NEW_OPERATOR
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.COMPANION_OBJECT_ACCESS
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.FUNCTION_CALL
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.IMPLICIT_GET
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.IMPLICIT_INVOKE
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.IMPLICIT_SET
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.SUPER_DELEGATION
import org.jetbrains.kotlin.idea.base.searching.usages.UsageTypeEnum.SUPER_TYPE
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.references.KtArrayAccessReference
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtWhenConditionInRange
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

@K1Deprecation
class KotlinUsageTypeProviderImpl : KotlinUsageTypeProvider() {

    override fun getUsageTypeEnumByReference(refExpr: KtReferenceExpression): UsageTypeEnum? {

        val context = refExpr.analyze(BodyResolveMode.PARTIAL)

        fun getFunctionUsageTypeDescriptor(descriptor: FunctionDescriptor): UsageTypeEnum? {
            when (refExpr.mainReference) {
                is KtArrayAccessReference -> {
                    return when {
                        context[BindingContext.INDEXED_LVALUE_GET, refExpr] != null -> IMPLICIT_GET
                        context[BindingContext.INDEXED_LVALUE_SET, refExpr] != null -> IMPLICIT_SET
                        else -> null
                    }
                }
                is KtInvokeFunctionReference -> return IMPLICIT_INVOKE
            }

            return when {
                refExpr.getParentOfTypeAndBranch<KtSuperTypeListEntry> { typeReference } != null -> SUPER_TYPE

                descriptor is ConstructorDescriptor && refExpr.getParentOfTypeAndBranch<KtAnnotationEntry> { typeReference } != null -> ANNOTATION

                with(refExpr.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression }) {
                    this?.calleeExpression is KtSimpleNameExpression
                } -> {
                    val callExpression = refExpr.getParentOfTypeAndBranch<KtCallExpression> { calleeExpression }
                    val qualifiedCall = callExpression?.parent as? KtDotQualifiedExpression

                    if (qualifiedCall?.receiverExpression is KtSuperExpression && qualifiedCall.selectorExpression == callExpression)
                        SUPER_DELEGATION
                    else if (descriptor is ConstructorDescriptor)
                        CLASS_NEW_OPERATOR
                    else
                        FUNCTION_CALL
                }

                refExpr.getParentOfTypeAndBranch<KtBinaryExpression> { operationReference } != null || refExpr.getParentOfTypeAndBranch<KtUnaryExpression> { operationReference } != null || refExpr.getParentOfTypeAndBranch<KtWhenConditionInRange> { operationReference } != null -> FUNCTION_CALL

                else -> null
            }
        }

        return when (val descriptor = context[BindingContext.REFERENCE_TARGET, refExpr]) {
            is ClassifierDescriptor -> when {
                // Treat object accesses as variables to simulate the old behaviour (when variables were created for objects)
                DescriptorUtils.isNonCompanionObject(descriptor) || DescriptorUtils.isEnumEntry(descriptor) -> getVariableUsageType(refExpr)
                DescriptorUtils.isCompanionObject(descriptor) -> COMPANION_OBJECT_ACCESS
                else -> getClassUsageType(refExpr)
            }
            is PackageViewDescriptor -> {
                if (refExpr.mainReference.resolve() is PsiPackage) getPackageUsageType(refExpr) else getClassUsageType(refExpr)
            }
            is VariableDescriptor -> getVariableUsageType(refExpr)
            is FunctionDescriptor -> getFunctionUsageTypeDescriptor(descriptor)
            else -> null
        }
    }
}
