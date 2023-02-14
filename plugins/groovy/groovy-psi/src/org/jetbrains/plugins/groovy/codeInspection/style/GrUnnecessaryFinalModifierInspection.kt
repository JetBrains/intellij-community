// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition

class GrUnnecessaryFinalModifierInspection : GrUnnecessaryModifierInspection(PsiModifier.FINAL) {

  override fun isRedundant(element: PsiElement): Boolean {
    val modifierList = element.parentOfType<GrModifierList>() ?: return false
    val owner = modifierList.parentOfType<PsiModifierListOwner>() ?: return false
    return owner is GrRecordDefinition && owner.modifierList === modifierList
  }
}