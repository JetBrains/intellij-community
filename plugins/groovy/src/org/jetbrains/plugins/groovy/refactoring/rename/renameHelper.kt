// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMember

fun PsiElement?.getNewNameFromTransformations(newName: String): String = (this as? PsiMember)?.let {
  doGetNewNameFromTransformations(it, newName)
} ?: newName

private fun doGetNewNameFromTransformations(member: PsiMember, newName: String): String? {
  for (helper in GrRenameHelper.EP_NAME.extensions) {
    val newMemberName = helper.getNewMemberName(member, newName)
    if (newMemberName != null) return newMemberName
  }
  return null
}

fun isQualificationNeeded(manager: PsiManager, before: PsiElement, after: PsiElement): Boolean = GrRenameHelper.EP_NAME.extensions.any {
  it.isQualificationNeeded(manager, before, after)
}