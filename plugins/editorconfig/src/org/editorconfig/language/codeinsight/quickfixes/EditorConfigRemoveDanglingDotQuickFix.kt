// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigQualifiedOptionKey
import org.editorconfig.language.services.EditorConfigElementFactory
import org.editorconfig.language.util.EditorConfigPsiTreeUtil.getParentOfType

class EditorConfigRemoveDanglingDotQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle["quickfix.dangling-dot.remove.description"]
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val dot = descriptor.psiElement ?: return
    val key = dot.getParentOfType<EditorConfigQualifiedOptionKey>() ?: return
    val manager = CodeStyleManager.getInstance(project)
    manager.performActionWithFormatterDisabled(dot::delete)
    val factory = EditorConfigElementFactory.getInstance(project)
    val newKey = factory.createKey(key.text)
    manager.performActionWithFormatterDisabled { key.replace(newKey) }
  }
}
