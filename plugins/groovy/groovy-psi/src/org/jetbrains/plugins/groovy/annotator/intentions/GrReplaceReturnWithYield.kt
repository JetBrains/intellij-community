// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement

class GrReplaceReturnWithYield : IntentionAction {
  override fun startInWriteAction(): Boolean {
    return true
  }

  override fun getText(): String = GroovyBundle.message("intention.name.replace", "return", "yield")

  override fun getFamilyName(): String = GroovyBundle.message("intention.family.name.replace.keywords")

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    editor ?: return false
    file ?: return false
    file.findElementAt(editor.caretModel.offset)?.parentOfType<GrReturnStatement>() ?: return false
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    editor ?: return
    file ?: return
    val elementUnderCaret = file.findElementAt(editor.caretModel.offset)?.parentOfType<GrReturnStatement>() ?: return
    val returnWord = elementUnderCaret.returnWord
    editor.document.replaceString(returnWord.startOffset, returnWord.endOffset, "yield")
  }
}