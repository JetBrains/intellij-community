// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition

class GrUnnecessaryFinalModifierInspection : GrUnnecessaryModifierInspection(PsiModifier.FINAL) {

  override fun isRedundant(element: PsiElement): Boolean {
    val modifierList = element.parentOfType<GrModifierList>() ?: return false
    val owner = modifierList.parentOfType<PsiModifierListOwner>() ?: return false
    if (owner.modifierList !== modifierList) return false
    if (owner is GrVariableDeclaration && modifierList.hasModifierProperty(GrModifier.VAL)) return true
    return owner is GrRecordDefinition
  }
}