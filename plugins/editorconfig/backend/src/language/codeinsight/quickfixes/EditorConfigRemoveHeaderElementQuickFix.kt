// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.interfaces.EditorConfigHeaderElement
import org.editorconfig.language.services.EditorConfigElementFactory
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.jetbrains.annotations.Nls

class EditorConfigRemoveHeaderElementQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.header-element.remove.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val pattern = descriptor.psiElement as? EditorConfigHeaderElement ?: return
    val header = pattern.header

    val globalRange = EditorConfigPsiTreeUtil.findRemovableRange(pattern)
    val headerOffset = header.textRange.startOffset
    val parentRange = globalRange.start - headerOffset..globalRange.endInclusive - headerOffset

    val text: CharSequence = header.text
    val newText = text.removeRange(parentRange)

    val factory = EditorConfigElementFactory.getInstance(project)
    val newHeader = factory.createHeader(newText)

    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { header.replace(newHeader) }
  }
}
