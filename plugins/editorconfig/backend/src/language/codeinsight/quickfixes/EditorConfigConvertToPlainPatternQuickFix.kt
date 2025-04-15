// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigCharClassPattern
import org.editorconfig.language.services.EditorConfigElementFactory
import org.jetbrains.annotations.Nls

class EditorConfigConvertToPlainPatternQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.charclass.convert.to.plain.pattern.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val charClass = descriptor.psiElement as? EditorConfigCharClassPattern ?: return
    val header = charClass.header
    val letter = charClass.charClassLetterList.first()

    val headerOffset = header.textOffset
    val range = charClass.textRange
    val actualRange = range.startOffset - headerOffset until range.endOffset - headerOffset

    val text = header.text
    val newText = text.replaceRange(actualRange, letter.text)

    val factory = EditorConfigElementFactory.getInstance(project)
    val newHeader = factory.createHeader(newText)

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { header.replace(newHeader) }
  }
}
