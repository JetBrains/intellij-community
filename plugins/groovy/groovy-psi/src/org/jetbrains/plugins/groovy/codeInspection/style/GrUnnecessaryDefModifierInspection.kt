/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDEF
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.util.isDefUnnecessary
import javax.swing.JComponent

class GrUnnecessaryDefModifierInspection : GroovySuppressableInspectionTool(), CleanupLocalInspectionTool {

  companion object {
    private val FIX = GrRemoveModifierFix(GrModifier.DEF)
  }

  @JvmField var reportExplicitTypeOnly: Boolean = true

  override fun createOptionsPanel(): JComponent? = MultipleCheckboxOptionsPanel(this).apply {
    addCheckbox(GroovyInspectionBundle.message("unnecessary.def.explicitly.typed.only"), "reportExplicitTypeOnly")
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {

    override fun visitElement(modifier: PsiElement) {
      if (modifier.node.elementType !== kDEF) return
      val modifierList = modifier.parent as? GrModifierList ?: return
      if (!isModifierUnnecessary(modifierList)) return
      holder.registerProblem(
        modifier,
        GroovyInspectionBundle.message("unnecessary.modifier.description", GrModifier.DEF),
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
        if (parent is GrForClause && parent.declaredVariable != owner) return false
        if (owner.typeElementGroovy != null) return true
        !reportExplicitTypeOnly && (parent is GrParameterList || parent is GrForInClause && parent.delimiter.node.elementType == kIN)
      }
      is GrMethod -> isDefUnnecessary(owner)
      is GrVariable -> owner.typeElementGroovy != null
      is GrVariableDeclaration -> owner.typeElementGroovy != null
      else -> false
    }
  }
}
