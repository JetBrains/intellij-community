// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pullUp

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.anonymousObjectSuperTypeOrNull
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.isUnit

fun KtClassOrObject.getSuperTypeEntryByDescriptor(
    descriptor: ClassDescriptor,
    context: BindingContext
): KtSuperTypeListEntry? {
    return superTypeListEntries.firstOrNull {
        val referencedType = context[BindingContext.TYPE, it.typeReference]
        referencedType?.constructor?.declarationDescriptor == descriptor
    }
}

fun makeAbstract(
    member: KtCallableDeclaration,
    originalMemberDescriptor: CallableMemberDescriptor,
    substitutor: TypeSubstitutor,
    targetClass: KtClass
) {
    if (!targetClass.isInterface()) {
        member.addModifier(KtTokens.ABSTRACT_KEYWORD)
    }

    val builtIns = originalMemberDescriptor.builtIns
    if (member.typeReference == null) {
        var type = originalMemberDescriptor.returnType
        if (type == null || type.isError) {
            type = builtIns.nullableAnyType
        } else {
            type = substitutor.substitute(type.anonymousObjectSuperTypeOrNull() ?: type, Variance.INVARIANT)
                ?: builtIns.nullableAnyType
        }

        if (member is KtProperty || !type.isUnit()) {
            member.setType(type, false)
        }
    }

    val deleteFrom = when (member) {
        is KtProperty -> {
            member.equalsToken ?: member.delegate ?: member.accessors.firstOrNull()
        }

        is KtNamedFunction -> {
            member.equalsToken ?: member.bodyExpression
        }

        else -> null
    }

    if (deleteFrom != null) {
        member.deleteChildRange(deleteFrom, member.lastChild)
    }
}

fun addSuperTypeEntry(
    delegator: KtSuperTypeListEntry,
    targetClass: KtClassOrObject,
    targetClassDescriptor: ClassDescriptor,
    context: BindingContext,
    substitutor: TypeSubstitutor
) {
    val referencedType = context[BindingContext.TYPE, delegator.typeReference]!!
    val referencedClass = referencedType.constructor.declarationDescriptor as? ClassDescriptor ?: return

    if (targetClassDescriptor == referencedClass || DescriptorUtils.isDirectSubclass(targetClassDescriptor, referencedClass)) return

    val typeInTargetClass = substitutor.substitute(referencedType, Variance.INVARIANT)
    if (!(typeInTargetClass != null && !typeInTargetClass.isError)) return

    val renderedType = IdeDescriptorRenderers.SOURCE_CODE.renderType(typeInTargetClass)
    val newSpecifier = KtPsiFactory(targetClass.project).createSuperTypeEntry(renderedType)
    targetClass.addSuperTypeListEntry(newSpecifier).addToShorteningWaitSet()
}
