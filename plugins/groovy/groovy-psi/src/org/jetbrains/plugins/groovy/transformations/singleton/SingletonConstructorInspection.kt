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

package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue
import org.jetbrains.plugins.groovy.transformations.message

class SingletonConstructorInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {

    override fun visitElement(element: PsiElement) {
      if (element.node.elementType !== GroovyTokenTypes.mIDENT) return
      val annotation = getAnnotation(element) ?: return
      val strict = annotation.findDeclaredDetachedValue("strict").booleanValue() ?: true
      if (!strict) return

      holder.registerProblem(
        element,
        message("singleton.constructor.found"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        RemoveElementQuickFix(message("singleton.constructor.remove")) { e -> e.parent },
        MakeNonStrictQuickFix()
      )
    }
  }
}
