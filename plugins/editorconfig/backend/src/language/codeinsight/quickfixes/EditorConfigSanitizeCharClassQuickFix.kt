// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigCharClassLetter
import org.editorconfig.language.psi.EditorConfigCharClassPattern
import org.editorconfig.language.services.EditorConfigElementFactory
import org.jetbrains.annotations.Nls

class EditorConfigSanitizeCharClassQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.charclass.sanitize.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val charClass = descriptor.psiElement as? EditorConfigCharClassPattern ?: return
    val first = charClass.charClassLetterList.first()
    val last = charClass.charClassLetterList.last()
    val prefix = charClass.text.substring(0, first.startOffsetInParent)
    val postfix = charClass.text.substring(last.startOffsetInParent + last.textLength)

    val newSource = distinctLetters(charClass.charClassLetterList)
      .asSequence()
      .map(EditorConfigCharClassLetter::getText)
      .fold(StringBuilder(prefix), StringBuilder::append)
      .append(postfix)
      .toString()

    val factory = EditorConfigElementFactory.getInstance(project)
    val newCharClass = factory.createCharClassPattern(newSource)

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { charClass.replace(newCharClass) }
  }

  private fun distinctLetters(letters: List<EditorConfigCharClassLetter>): List<EditorConfigCharClassLetter> {
    val result = mutableListOf<EditorConfigCharClassLetter>()
    letters.forEach {
      if (result.none(it::textMatches)) {
        result.add(it)
      }
    }
    return result
  }
}
