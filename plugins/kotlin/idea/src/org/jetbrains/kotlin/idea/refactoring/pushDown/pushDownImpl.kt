// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pushDown

import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.refactoring.pullUp.addMemberToTarget
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.util.findCallableMemberBySignature

internal fun moveCallableMemberToClass(
    member: KtCallableDeclaration,
    memberDescriptor: CallableMemberDescriptor,
    targetClass: KtClassOrObject,
    targetClassDescriptor: ClassDescriptor,
    substitutor: TypeSubstitutor,
    makeAbstract: Boolean
): KtCallableDeclaration {
    val targetMemberDescriptor = memberDescriptor.substitute(substitutor)?.let {
        targetClassDescriptor.findCallableMemberBySignature(it as CallableMemberDescriptor)
    }
    val targetMember = targetMemberDescriptor?.source?.getPsi() as? KtCallableDeclaration
    return targetMember?.apply {
        if (memberDescriptor.modality != Modality.ABSTRACT && makeAbstract) {
            addModifier(KtTokens.OVERRIDE_KEYWORD)
        } else if (memberDescriptor.overriddenDescriptors.isEmpty()) {
            removeModifier(KtTokens.OVERRIDE_KEYWORD)
        } else {
            addModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    } ?: addMemberToTarget(member, targetClass).apply {
        val sourceClassDescriptor = memberDescriptor.containingDeclaration as? ClassDescriptor
        if (sourceClassDescriptor?.kind == ClassKind.INTERFACE) {
            if (targetClassDescriptor.kind != ClassKind.INTERFACE && memberDescriptor.modality == Modality.ABSTRACT) {
                addModifier(KtTokens.ABSTRACT_KEYWORD)
            }
        }
        if (memberDescriptor.modality != Modality.ABSTRACT && makeAbstract) {
            KtTokens.VISIBILITY_MODIFIERS.types.forEach { removeModifier(it as KtModifierKeywordToken) }
            addModifier(KtTokens.OVERRIDE_KEYWORD)
        }
    } as KtCallableDeclaration
}