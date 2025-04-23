// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.editorconfig.frontend.editor

import com.intellij.codeInsight.editorActions.moveUpDown.LineMover
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.editorconfig.common.syntax.psi.EditorConfigOption
import com.intellij.editorconfig.common.syntax.psi.EditorConfigPsiFile
import com.intellij.editorconfig.common.syntax.psi.EditorConfigSection
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

internal class EditorConfigStatementUpDownMover : LineMover() {

  override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
    if (file !is EditorConfigPsiFile) return false
    if (editor.selectionModel.hasSelection()) {
      return super.checkAvailable(editor, file, info, down)
    }

    val range = getLineRangeFromSelection(editor)
    if (!canMove(editor, range, down)) return false
    val (startElement, _) = getElementRange(editor, file, range) ?: return false
    val option = startElement.parentOfType<EditorConfigOption>(withSelf = true)
    if (option != null) return orderMoveOption(info, down, option)
    val section = startElement.parentOfType<EditorConfigSection>(withSelf = true)
    if (section != null) return orderMoveSection(info, down, section)
    return info.prohibitMove()
  }

  private fun orderMoveOption(info: MoveInfo, down: Boolean, option: EditorConfigOption): Boolean {
    info.toMove = LineRange(option, option)

    val otherOptionInSection =
      if (down) PsiTreeUtil.getNextSiblingOfType(option, EditorConfigOption::class.java)
      else PsiTreeUtil.getPrevSiblingOfType(option, EditorConfigOption::class.java)

    if (otherOptionInSection != null) {
      info.toMove2 = LineRange(otherOptionInSection, otherOptionInSection)
      return true
    }

    val section = option.section
    val otherSection = findOtherSection(section, down)
    otherSection ?: return info.prohibitMove()

    if (down) {
      val nextLine = info.toMove.endLine
      val headerLine = LineRange(otherSection.header, otherSection.header).endLine
      info.toMove2 = LineRange(nextLine, headerLine)
    }
    else {
      val otherSectionLineRange = LineRange(otherSection, otherSection)
      val lastLineInOtherSection = otherSectionLineRange.endLine
      val lineBeforeCurrentOption = info.toMove.startLine
      info.toMove2 = LineRange(lastLineInOtherSection, lineBeforeCurrentOption)
    }

    return true
  }

  private fun orderMoveSection(info: MoveInfo, down: Boolean, section: EditorConfigSection): Boolean {
    val otherSection = findOtherSection(section, down)

    otherSection ?: return info.prohibitMove()

    info.toMove = LineRange(section, section)
    info.toMove2 = LineRange(otherSection, otherSection)
    return true
  }

  private fun findOtherSection(section: EditorConfigSection, down: Boolean) =
    if (down) PsiTreeUtil.getNextSiblingOfType(section, EditorConfigSection::class.java)
    else PsiTreeUtil.getPrevSiblingOfType(section, EditorConfigSection::class.java)

  private fun canMove(editor: Editor, range: LineRange, down: Boolean): Boolean {
    if (range.startLine == 0 && !down) return false
    val maxLine = editor.offsetToLogicalPosition(editor.document.textLength).line
    return !down || range.endLine <= maxLine
  }
}
