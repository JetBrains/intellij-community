// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.language.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.TextRange
import org.editorconfig.language.codeinsight.quickfixes.EditorConfigSanitizeCharClassQuickFix
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigCharClassLetter
import org.editorconfig.language.psi.EditorConfigCharClassPattern
import org.editorconfig.language.psi.EditorConfigVisitor

class EditorConfigCharClassLetterRedundancyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : EditorConfigVisitor() {
    override fun visitCharClassPattern(charClass: EditorConfigCharClassPattern) {
      val letters = charClass.charClassLetterList
      val message = EditorConfigBundle["inspection.charclass.duplicate.message"]

      // Ranges are collected this way
      // in order to avoid overwhelming user
      // with a bunch of one-character warnings

      var state = State.INITIAL
      var firstDuplicateStart = Int.MAX_VALUE

      letters.forEach {
        val unique = isUnique(it, letters)
        if (unique && state == State.COLLECTING_DUPLICATES) {
          val range = TextRange.create(firstDuplicateStart, it.startOffsetInParent)
          holder.registerProblem(charClass, range, message, EditorConfigSanitizeCharClassQuickFix())
          state = State.INITIAL
        }
        else if (!unique && state == State.INITIAL) {
          firstDuplicateStart = it.startOffsetInParent
          state = State.COLLECTING_DUPLICATES
        }
      }

      if (state == State.COLLECTING_DUPLICATES) {
        val last = letters.last()
        val range = TextRange.create(firstDuplicateStart, last.startOffsetInParent + last.textLength)
        holder.registerProblem(charClass, range, message, EditorConfigSanitizeCharClassQuickFix())
      }
    }
  }

  private fun isUnique(letter: EditorConfigCharClassLetter, letters: List<EditorConfigCharClassLetter>) =
    letters.count(letter::textMatches) <= 1

  private enum class State {
    INITIAL,
    COLLECTING_DUPLICATES
  }
}
