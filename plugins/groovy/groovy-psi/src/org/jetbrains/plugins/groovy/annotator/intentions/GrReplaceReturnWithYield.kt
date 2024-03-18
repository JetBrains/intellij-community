// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement

class GrReplaceReturnWithYield : PsiUpdateModCommandAction<GrReturnStatement>(GrReturnStatement::class.java) {
  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.replace.keywords")

  override fun getPresentation(context: ActionContext, element: GrReturnStatement): Presentation {
    return Presentation.of(GroovyBundle.message("intention.name.replace", "return", "yield"))
  }

  override fun invoke(context: ActionContext, elementUnderCaret: GrReturnStatement, updater: ModPsiUpdater) {
    val returnWord = elementUnderCaret.returnWord
    elementUnderCaret.containingFile.viewProvider.document.replaceString(returnWord.startOffset, returnWord.endOffset, "yield")
  }
}