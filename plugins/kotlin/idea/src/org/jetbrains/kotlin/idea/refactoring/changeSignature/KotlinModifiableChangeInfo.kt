// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import org.jetbrains.kotlin.descriptors.Visibility

interface KotlinModifiableChangeInfo<P : KotlinModifiableParameterInfo> : ChangeInfo {
  fun addParameter(parameterInfo: P, atIndex: Int = -1)
  fun removeParameter(index: Int)
  fun clearParameters()
  fun setNewName(value: String)
  fun setNewParameter(index: Int, parameterInfo: P)
  fun setNewVisibility(visibility: Visibility)
  val context: PsiElement

  var receiverParameterInfo: P?
  var primaryPropagationTargets: Collection<PsiElement>

  override fun getNewParameters(): Array<out P>

  fun setType(type: String)
}
