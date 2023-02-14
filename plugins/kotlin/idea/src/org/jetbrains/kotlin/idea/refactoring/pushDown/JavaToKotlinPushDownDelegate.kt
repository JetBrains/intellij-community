// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pushDown

import com.intellij.psi.*
import com.intellij.refactoring.memberPushDown.JavaPushDownDelegate
import com.intellij.refactoring.memberPushDown.NewSubClassData
import com.intellij.refactoring.memberPushDown.PushDownData
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.refactoring.util.classMembers.MemberInfo
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaClassDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.util.getJavaMemberDescriptor
import org.jetbrains.kotlin.idea.core.getOrCreateCompanionObject
import org.jetbrains.kotlin.idea.j2k.j2k
import org.jetbrains.kotlin.idea.j2k.j2kText
import org.jetbrains.kotlin.idea.refactoring.isInterfaceClass
import org.jetbrains.kotlin.idea.refactoring.pullUp.addMemberToTarget
import org.jetbrains.kotlin.idea.util.getTypeSubstitution
import org.jetbrains.kotlin.idea.util.orEmpty
import org.jetbrains.kotlin.idea.util.toSubstitutor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtPsiFactory

class JavaToKotlinPushDownDelegate : JavaPushDownDelegate() {
    override fun checkTargetClassConflicts(
        targetClass: PsiElement?,
        pushDownData: PushDownData<MemberInfo, PsiMember>,
        conflicts: MultiMap<PsiElement, String>,
        subClassData: NewSubClassData?
    ) {
        super.checkTargetClassConflicts(targetClass, pushDownData, conflicts, subClassData)

        val ktClass = targetClass?.unwrapped as? KtClassOrObject ?: return
        val targetClassDescriptor = ktClass.unsafeResolveToDescriptor() as ClassDescriptor
        for (memberInfo in pushDownData.membersToMove) {
            val member = memberInfo.member ?: continue
            checkExternalUsages(conflicts, member, targetClassDescriptor, ktClass.getResolutionFacade())
        }
    }

    override fun pushDownToClass(targetClass: PsiElement, pushDownData: PushDownData<MemberInfo, PsiMember>) {
        val superClass = pushDownData.sourceClass as? PsiClass ?: return
        val subClass = targetClass.unwrapped as? KtClassOrObject ?: return
        val resolutionFacade = subClass.getResolutionFacade()
        val superClassDescriptor = superClass.getJavaClassDescriptor(resolutionFacade) ?: return
        val subClassDescriptor = subClass.unsafeResolveToDescriptor() as ClassDescriptor
        val substitutor = getTypeSubstitution(superClassDescriptor.defaultType, subClassDescriptor.defaultType)?.toSubstitutor().orEmpty()
        val psiFactory = KtPsiFactory(subClass.project)
        var hasAbstractMembers = false
        members@ for (memberInfo in pushDownData.membersToMove) {
            val member = memberInfo.member
            val memberDescriptor = member.getJavaMemberDescriptor(resolutionFacade) ?: continue
            when (member) {
                is PsiMethod, is PsiField -> {
                    val ktMember = member.j2k() as? KtCallableDeclaration ?: continue@members
                    ktMember.removeModifier(KtTokens.DEFAULT_VISIBILITY_KEYWORD)
                    val isStatic = member.hasModifierProperty(PsiModifier.STATIC)
                    val targetMemberClass = if (isStatic && subClass is KtClass) subClass.getOrCreateCompanionObject() else subClass
                    val targetMemberClassDescriptor = resolutionFacade.resolveToDescriptor(targetMemberClass) as ClassDescriptor
                    if (member.hasModifierProperty(PsiModifier.ABSTRACT)) {
                        hasAbstractMembers = true
                    }
                    moveCallableMemberToClass(
                        ktMember,
                        memberDescriptor as CallableMemberDescriptor,
                        targetMemberClass,
                        targetMemberClassDescriptor,
                        substitutor,
                        memberInfo.isToAbstract
                    ).apply {
                        if (subClass.isInterfaceClass()) {
                            removeModifier(KtTokens.ABSTRACT_KEYWORD)
                        }
                    }
                }

                is PsiClass -> {
                    if (memberInfo.overrides != null) {
                        val typeText =
                            RefactoringUtil.findReferenceToClass(superClass.implementsList, member)?.j2kText() ?: continue@members
                        subClass.addSuperTypeListEntry(psiFactory.createSuperTypeEntry(typeText))
                    } else {
                        val ktClass = member.j2k() as? KtClassOrObject ?: continue@members
                        addMemberToTarget(ktClass, subClass)
                    }
                }
            }
        }

        if (hasAbstractMembers && !subClass.isInterfaceClass()) {
            subClass.addModifier(KtTokens.ABSTRACT_KEYWORD)
        }
    }
}
