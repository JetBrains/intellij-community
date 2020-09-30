// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.transformations.singleton

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.fixes.RemoveElementQuickFix
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.impl.booleanValue
import org.jetbrains.plugins.groovy.lang.psi.impl.findDeclaredDetachedValue

class SingletonConstructorInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {

    override fun visitElement(element: PsiElement) {
      if (element.node.elementType !== GroovyTokenTypes.mIDENT) return
      val annotation = getAnnotation(element) ?: return
      val strict = annotation.findDeclaredDetachedValue("strict").booleanValue() ?: true
      if (!strict) return

      holder.registerProblem(
        element,
        GroovyBundle.message("singleton.constructor.found"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        RemoveElementQuickFix(GroovyBundle.message("singleton.constructor.remove")) { e -> e.parent },
        MakeNonStrictQuickFix()
      )
    }
  }
}
