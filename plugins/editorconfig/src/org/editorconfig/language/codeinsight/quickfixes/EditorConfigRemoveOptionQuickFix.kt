// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement

class EditorConfigRemoveOptionQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle.get("quickfix.option.remove.description")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? EditorConfigDescribableElement ?: return
    val option = element.option
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled(option::delete)
  }
}
