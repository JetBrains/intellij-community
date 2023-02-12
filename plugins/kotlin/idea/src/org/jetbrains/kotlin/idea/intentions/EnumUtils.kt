// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.base.codeInsight.isEnumValuesSoftDeprecateEnabled
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.utils.ENUM_STATIC_METHOD_NAMES
import org.jetbrains.kotlin.idea.codeinsight.utils.ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES
import org.jetbrains.kotlin.idea.codeinsight.utils.canBeReferenceToBuiltInEnumFunction
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.inspections.collections.isCalling
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.tower.isSynthesized
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

private val KOTLIN_BUILTIN_ENUM_FUNCTIONS = listOf(FqName("kotlin.enumValues"), FqName("kotlin.enumValueOf"))

internal fun KtTypeReference.isReferenceToBuiltInEnumFunction(): Boolean {
    val target = (parent.getStrictParentOfType<KtTypeArgumentList>() ?: this)
        .getParentOfTypes(true, KtCallExpression::class.java, KtCallableDeclaration::class.java)
    return when (target) {
        is KtCallExpression -> target.isCalling(KOTLIN_BUILTIN_ENUM_FUNCTIONS)
        is KtCallableDeclaration -> {
            target.anyDescendantOfType<KtCallExpression> {
                val context = it.analyze(BodyResolveMode.PARTIAL_WITH_CFA)
                it.isCalling(KOTLIN_BUILTIN_ENUM_FUNCTIONS, context) && it.isUsedAsExpression(context)
            }
        }

        else -> false
    }
}

internal fun KtCallExpression.isReferenceToBuiltInEnumFunction(): Boolean {
    val calleeExpression = this.calleeExpression ?: return false
    return (calleeExpression as? KtSimpleNameExpression)?.getReferencedNameAsName() in ENUM_STATIC_METHOD_NAMES && calleeExpression.isSynthesizedEnumFunction()
}

internal fun KtCallableReferenceExpression.isReferenceToBuiltInEnumFunction(): Boolean {
    return this.canBeReferenceToBuiltInEnumFunction() && this.callableReference.isSynthesizedEnumFunction()
}

internal fun KtSimpleNameExpression.isReferenceToBuiltInEnumEntries(): Boolean =
    isEnumValuesSoftDeprecateEnabled() && this.getReferencedNameAsName() == StandardNames.ENUM_ENTRIES && isSynthesizedEnumFunction()

internal fun KtImportDirective.isUsedStarImportOfEnumStaticFunctions(): Boolean {
    if (importPath?.isAllUnder != true) return false
    val importedEnumFqName = this.importedFqName ?: return false
    val classDescriptor = targetDescriptors().filterIsInstance<ClassDescriptor>().firstOrNull() ?: return false
    if (classDescriptor.kind != ClassKind.ENUM_CLASS) return false

    val enumStaticMethods = ENUM_STATIC_METHOD_NAMES_WITH_ENTRIES.map { FqName("$importedEnumFqName.$it") }

    fun KtExpression.isFqNameInEnumStaticMethods(): Boolean {
        if (getQualifiedExpressionForSelector() != null) return false
        if (((this as? KtNameReferenceExpression)?.parent as? KtCallableReferenceExpression)?.receiverExpression != null) return false
        val descriptor = this.resolveToCall(BodyResolveMode.PARTIAL_WITH_CFA)?.resultingDescriptor
        return descriptor?.fqNameSafe in enumStaticMethods
    }

    return containingFile.anyDescendantOfType<KtExpression> {
        (it as? KtCallExpression)?.isFqNameInEnumStaticMethods() == true
                || (it as? KtCallableReferenceExpression)?.callableReference?.isFqNameInEnumStaticMethods() == true
                || (it as? KtReferenceExpression)?.isFqNameInEnumStaticMethods() == true
    }
}


private fun KtExpression.isSynthesizedEnumFunction(): Boolean {
    val descriptor = this.resolveToCall(BodyResolveMode.PARTIAL_WITH_CFA)?.resultingDescriptor ?: return false
    return descriptor.isSynthesized
}