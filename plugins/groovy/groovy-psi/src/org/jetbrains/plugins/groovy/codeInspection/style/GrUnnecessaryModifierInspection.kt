// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl

abstract class GrUnnecessaryModifierInspection(@GrModifier.GrModifierConstant val modifier: String) : LocalInspectionTool(), CleanupLocalInspectionTool {
  private fun getFix(): GrModifierFix {
    return GrRemoveModifierFix(modifier)
  }

  private val requiredElementType: IElementType by lazy { GrModifierListImpl.NAME_TO_MODIFIER_ELEMENT_TYPE[modifier]!! }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element.elementType === requiredElementType && isRedundant(element)) {
        holder.registerProblem(
          element,
          GroovyBundle.message("unnecessary.modifier.description", modifier),
          getFix()
        )
      }
    }
  }

  /**
   * [element] has element type that corresponds to [modifier]
   */
  abstract fun isRedundant(element: PsiElement): Boolean
}