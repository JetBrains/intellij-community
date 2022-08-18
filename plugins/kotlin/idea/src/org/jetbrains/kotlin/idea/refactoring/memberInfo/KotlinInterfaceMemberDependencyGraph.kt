// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.memberInfo

import com.intellij.psi.PsiMember
import com.intellij.refactoring.classMembers.MemberDependencyGraph
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.util.classMembers.InterfaceMemberDependencyGraph
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration

class KotlinInterfaceMemberDependencyGraph<T : KtNamedDeclaration, M : MemberInfoBase<T>>(
    klass: KtClassOrObject
) : MemberDependencyGraph<T, M> {
    private val delegateGraph: MemberDependencyGraph<PsiMember, MemberInfoBase<PsiMember>> =
        InterfaceMemberDependencyGraph(klass.toLightClass())

    override fun memberChanged(memberInfo: M) {
        delegateGraph.memberChanged(memberInfo.toJavaMemberInfo() ?: return)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getDependent() = delegateGraph.dependent
        .asSequence()
        .mapNotNull { it.unwrapped }
        .filterIsInstanceTo(LinkedHashSet<KtNamedDeclaration>()) as Set<T>

    @Suppress("UNCHECKED_CAST")
    override fun getDependenciesOf(member: T): Set<T> {
        val psiMember = lightElementForMemberInfo(member) ?: return emptySet()
        val psiMemberDependencies = delegateGraph.getDependenciesOf(psiMember) ?: return emptySet()
        return psiMemberDependencies
            .asSequence()
            .mapNotNull { it.unwrapped }
            .filterIsInstanceTo(LinkedHashSet<KtNamedDeclaration>()) as Set<T>
    }
}
