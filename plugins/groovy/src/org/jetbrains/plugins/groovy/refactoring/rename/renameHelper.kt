/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.refactoring.rename

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMember

fun PsiElement?.getNewNameFromTransformations(newName: String): String = (this as? PsiMember)?.let {
  doGetNewNameFromTransformations(it, newName)
} ?: newName

private fun doGetNewNameFromTransformations(member: PsiMember, newName: String): String? {
  @Suppress("LoopToCallChain")
  for (helper in GrRenameHelper.EP_NAME.extensions) {
    val newMemberName = helper.getNewMemberName(member, newName)
    if (newMemberName != null) return newMemberName
  }
  return null
}

fun isQualificationNeeded(manager: PsiManager, before: PsiElement, after: PsiElement): Boolean = GrRenameHelper.EP_NAME.extensions.any {
  it.isQualificationNeeded(manager, before, after)
}