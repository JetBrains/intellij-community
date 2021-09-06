// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl

sealed class GrUnnecessarySealingModifierInspection(val modifier: String) : LocalInspectionTool(), CleanupLocalInspectionTool {

  val elementType: IElementType = GrModifierListImpl.NAME_TO_MODIFIER_ELEMENT_TYPE[modifier]!!
  val fix = GrRemoveModifierFix(modifier)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {

    override fun visitElement(element: PsiElement) {
      if (element.node.elementType !== elementType) return
      val modifierList = element.parent as? GrModifierList ?: return
      val owner = modifierList.parentOfType<PsiModifierListOwner>()?.takeIf { it.modifierList === modifierList } ?: return
      if (owner !is GrTypeDefinition) {
        holder.registerProblem(
          element,
          GroovyBundle.message("unnecessary.modifier.description", modifier),
          ProblemHighlightType.LIKE_UNUSED_SYMBOL,
          fix
        )
      }
    }
  }
}

class GrUnnecessarySealedModifierInspection : GrUnnecessarySealingModifierInspection(GrModifier.SEALED)
class GrUnnecessaryNonSealedModifierInspection : GrUnnecessarySealingModifierInspection(GrModifier.NON_SEALED)
