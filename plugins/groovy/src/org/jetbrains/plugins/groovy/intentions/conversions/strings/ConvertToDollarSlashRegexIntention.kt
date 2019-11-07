// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions.strings

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil

class ConvertToDollarSlashRegexIntention : Intention() {

  override fun processIntention(element: PsiElement, project: Project, editor: Editor) {
    if (element !is GrLiteral) return
    val rawText = GrStringUtil.removeQuotes(element.getText())
    val newRegexp = GroovyPsiElementFactory.getInstance(project).createExpressionFromText("$/$rawText/$")
    element.replace(newRegexp) //don't use replaceWithExpression() since it can revert conversion if regexp brakes syntax
  }

  override fun getElementPredicate(): PsiElementPredicate = IntentionPredicate

  private object IntentionPredicate : PsiElementPredicate {

    override fun satisfiedBy(element: PsiElement): Boolean {
      return element is GrLiteral &&
             GrStringUtil.isStringLiteral(element) &&
             !GrStringUtil.isDollarSlashyString(element)
    }
  }
}
