// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.util.containers.MultiMap
import org.jetbrains.kotlin.idea.refactoring.memberInfo.KotlinMemberInfo
import org.jetbrains.kotlin.psi.KtClassOrObject

internal class K1PullUpConflictSearcher : KotlinPullUpConflictSearcher {
    override fun collectConflicts(
        sourceClass: KtClassOrObject,
        targetClass: PsiNamedElement,
        memberInfos: List<KotlinMemberInfo>
    ): MultiMap<PsiElement, String> {
        val result = MultiMap.create<PsiElement, String>()

        collectConflicts(sourceClass, targetClass, memberInfos, result)

        return result
    }
}
