// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.editor.actions

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_SQ
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_TSQ

class GroovyStringBackslashHandler : TypedHandlerDelegate() {

  override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (c == '\\') {
      val offset = editor.caretModel.offset
      if (shouldInsertAdditionalSlash(file, offset - 1)) { // document is not committed yet, so we have to subtract 1
        editor.document.insertString(offset, "\\")
        editor.selectionModel.setSelection(offset, offset + 1)
        return Result.DEFAULT
      }
    }
    return super.charTyped(c, project, editor, file)
  }

  private fun shouldInsertAdditionalSlash(file: PsiFile, offset: Int): Boolean {
    val literal = file.findElementAt(offset) ?: return false
    val tokenType = literal.node.elementType
    return tokenType == STRING_SQ && literal.textRange.endOffset - offset == 1 // 'bla bla <here>'
           || tokenType == STRING_TSQ && literal.textRange.endOffset - offset == 3 // '''bla bla <here>'''
  }
}
