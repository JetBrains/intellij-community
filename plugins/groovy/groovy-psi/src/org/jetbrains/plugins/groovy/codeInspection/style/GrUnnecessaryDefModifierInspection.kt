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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle
import org.jetbrains.plugins.groovy.codeInspection.GroovySuppressableInspectionTool
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDEF
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.util.hasOtherModifiers
import org.jetbrains.plugins.groovy.lang.psi.util.modifierListMayBeEmpty

class GrUnnecessaryDefModifierInspection : GroovySuppressableInspectionTool(), CleanupLocalInspectionTool {

  companion object {
    private val FIX = GrRemoveModifierFix(GrModifier.DEF)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : PsiElementVisitor() {

    override fun visitElement(modifier: PsiElement) {
      if (modifier.node.elementType !== kDEF) return
      val modifierList = modifier.parent as? GrModifierList ?: return
      val owner = modifierList.parent ?: return
      if (!modifierListMayBeEmpty(owner) && !modifierList.hasOtherModifiers(kDEF)) return

      holder.registerProblem(
        modifier,
        GroovyInspectionBundle.message("unnecessary.modifier.description", GrModifier.DEF),
        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
        FIX
      )
    }
  }
}
