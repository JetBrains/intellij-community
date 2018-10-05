// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigOption
import org.editorconfig.language.services.EditorConfigElementFactory

class EditorConfigRemoveLineBreaksInOptionQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle["quickfix.option.remove-line-breaks.description"]

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val option = descriptor.psiElement as? EditorConfigOption ?: return
    val newText = option.text.replace('\n', ' ')
    val factory = EditorConfigElementFactory.getInstance(project)
    val newOption = factory.createOption(newText)
    val manager = CodeStyleManager.getInstance(project)
    manager.reformat(newOption)
    manager.performActionWithFormatterDisabled {
      option.replace(newOption)
    }
  }
}
