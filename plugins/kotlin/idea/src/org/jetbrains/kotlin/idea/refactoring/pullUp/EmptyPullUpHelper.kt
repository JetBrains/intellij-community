// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.pullUp

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiSubstitutor
import com.intellij.refactoring.classMembers.MemberInfoBase
import com.intellij.refactoring.memberPullUp.PullUpHelper

object EmptyPullUpHelper : PullUpHelper<MemberInfoBase<PsiMember>> {
    override fun postProcessMember(member: PsiMember) {

    }

    override fun moveFieldInitializations(movedFields: LinkedHashSet<PsiField>) {

    }

    override fun encodeContextInfo(info: MemberInfoBase<PsiMember>) {

    }

    override fun setCorrectVisibility(info: MemberInfoBase<PsiMember>) {

    }

    override fun move(info: MemberInfoBase<PsiMember>, substitutor: PsiSubstitutor) {

    }

    override fun updateUsage(element: PsiElement) {

    }
}

