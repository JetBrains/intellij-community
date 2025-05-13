// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.pushDown

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.idea.codeInsight.shorten.addToShorteningWaitSet
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KtPsiClassWrapper
import org.jetbrains.kotlin.idea.refactoring.pullUp.*
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.orEmpty
import org.jetbrains.kotlin.idea.util.toSubstitutor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.TypeSubstitutor

class K1PushDownProcessor(
    project: Project,
    sourceClass: KtClass,
    membersToMove: List<KotlinMemberInfo>
) : KotlinPushDownProcessor(project) {
    override val context: K1PushDownContext =
        K1PushDownContext(sourceClass, membersToMove)

    override fun renderSourceClassForConflicts(): String =
        context.sourceClassDescriptor.renderForConflicts()

    override fun analyzePushDownConflicts(
        usages: Array<out UsageInfo>,
    ): MultiMap<PsiElement, String> =
        analyzePushDownConflicts(context, usages)

    override fun pushDownToClass(targetClass: KtClassOrObject) {
        val sourceClassType = context.sourceClassDescriptor.defaultType
        val targetClassDescriptor = context.resolutionFacade.resolveToDescriptor(targetClass) as ClassDescriptor
        val substitutor = getTypeSubstitution(sourceClassType, targetClassDescriptor.defaultType)?.toSubstitutor().orEmpty()
        members@ for (memberInfo in context.membersToMove) {
            val member = memberInfo.member
            val memberDescriptor = context.memberDescriptors[member] ?: continue

            val movedMember = when (member) {
                is KtProperty, is KtNamedFunction -> {
                    memberDescriptor as CallableMemberDescriptor

                    moveCallableMemberToClass(
                        member as KtCallableDeclaration,
                        memberDescriptor,
                        targetClass,
                        targetClassDescriptor,
                        substitutor,
                        memberInfo.isToAbstract
                    )
                }

                is KtClassOrObject, is KtPsiClassWrapper -> {
                    if (memberInfo.overrides != null) {
                        context.sourceClass.getSuperTypeEntryByDescriptor(
                            memberDescriptor as ClassDescriptor,
                            context.sourceClassContext
                        )?.let {
                            addSuperTypeEntry(it, targetClass, targetClassDescriptor, context.sourceClassContext, substitutor)
                        }
                        continue@members
                    } else {
                        addMemberToTarget(member, targetClass)
                    }
                }

                else -> continue@members
            }
            applyMarking(movedMember, substitutor, targetClassDescriptor)
        }
    }

    override fun removeOriginalMembers() {
        for (memberInfo in context.membersToMove) {
            val member = memberInfo.member
            val memberDescriptor = context.memberDescriptors[member] ?: continue
            when (member) {
                is KtProperty, is KtNamedFunction -> {
                    member as KtCallableDeclaration
                    memberDescriptor as CallableMemberDescriptor

                    if (memberDescriptor.modality != Modality.ABSTRACT && memberInfo.isToAbstract) {
                        if (member.hasModifier(KtTokens.PRIVATE_KEYWORD)) {
                            member.addModifier(KtTokens.PROTECTED_KEYWORD)
                        }
                        makeAbstract(member, memberDescriptor, TypeSubstitutor.EMPTY, context.sourceClass)
                        member.typeReference?.addToShorteningWaitSet()
                    } else {
                        member.delete()
                    }
                }
                is KtClassOrObject, is KtPsiClassWrapper -> {
                    if (memberInfo.overrides != null) {
                        context.sourceClass.getSuperTypeEntryByDescriptor(
                            memberDescriptor as ClassDescriptor,
                            context.sourceClassContext
                        )?.let {
                            context.sourceClass.removeSuperTypeListEntry(it)
                        }
                    } else {
                        member.delete()
                    }
                }
            }
        }
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val markedElements = ArrayList<KtElement>()
        try {
            context.membersToMove.forEach {
                markedElements += markElements(it.member, context.sourceClassContext, context.sourceClassDescriptor, null)
            }
            usages.forEach { (it.element as? KtClassOrObject)?.let { pushDownToClass(it) } }
            removeOriginalMembers()
        } finally {
            clearMarking(markedElements)
        }
    }
}