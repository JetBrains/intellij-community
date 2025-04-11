// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigEnumerationPattern
import org.editorconfig.language.services.EditorConfigElementFactory
import org.jetbrains.annotations.Nls

class EditorConfigRemoveBracesQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.pattern-enumeration.redundant.remove-braces.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val patternEnumeration = descriptor.psiElement as? EditorConfigEnumerationPattern ?: return
    val pattern = patternEnumeration.patternList.single()
    val header = patternEnumeration.header

    val headerStart = header.textRange.startOffset
    val range = patternEnumeration.textRange
    val actualRange = range.startOffset - headerStart until range.endOffset - headerStart

    val text: CharSequence = header.text
    val newText = text.replaceRange(actualRange, pattern.text)

    val factory = EditorConfigElementFactory.getInstance(project)
    val newHeader = factory.createHeader(newText)

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { header.replace(newHeader) }
  }
}
