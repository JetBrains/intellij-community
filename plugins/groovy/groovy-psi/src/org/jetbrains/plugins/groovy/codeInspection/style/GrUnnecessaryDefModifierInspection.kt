// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDEF
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.isDefUnnecessary
import javax.swing.JComponent

class GrUnnecessaryDefModifierInspection : LocalInspectionTool(), CleanupLocalInspectionTool {

  companion object {
    private val FIX = GrRemoveModifierFix(GrModifier.DEF)
  }

  @JvmField var reportExplicitTypeOnly: Boolean = true

  override fun createOptionsPanel(): JComponent? = MultipleCheckboxOptionsPanel(this).apply {
    addCheckbox(GroovyBundle.message("unnecessary.def.explicitly.typed.only"), "reportExplicitTypeOnly")
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {

    override fun visitElement(modifier: PsiElement) {
      if (modifier.node.elementType !== kDEF) return
      val modifierList = modifier.parent as? GrModifierList ?: return
      if (!isModifierUnnecessary(modifierList)) return
      holder.registerProblem(
        modifier,
        GroovyBundle.message("unnecessary.modifier.description", GrModifier.DEF),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        FIX
      )
    }
  }

  private fun isModifierUnnecessary(modifierList: GrModifierList): Boolean {
    fun GrModifierList.hasOtherModifiers() = modifiers.any { it.node.elementType != kDEF }

    if (!reportExplicitTypeOnly && modifierList.hasOtherModifiers()) return true

    val owner = modifierList.parent as? PsiModifierListOwner ?: return false
    return when (owner) {
      is GrParameter -> {
        val parent = owner.parent
        if (owner.typeElementGroovy != null) return true
        if (reportExplicitTypeOnly) return false
        parent is GrParameterList || parent is GrForInClause && parent.delimiter.node.elementType == kIN
      }
      is GrMethod -> isDefUnnecessary(owner)
      is GrVariable -> owner.typeElementGroovy != null
      is GrVariableDeclaration -> owner.typeElementGroovy != null
      else -> false
    }
  }
}
