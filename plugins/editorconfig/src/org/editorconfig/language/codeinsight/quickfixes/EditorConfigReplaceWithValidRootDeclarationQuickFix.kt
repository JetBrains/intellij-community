// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.EditorConfigRootDeclaration
import org.editorconfig.language.services.EditorConfigElementFactory

class EditorConfigReplaceWithValidRootDeclarationQuickFix : LocalQuickFix {
  override fun getFamilyName() = EditorConfigBundle.get("quickfix.root-declaration.replace-with-valid.description")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? EditorConfigRootDeclaration ?: return
    val elementFactory = EditorConfigElementFactory.getInstance(project)
    val replacement = elementFactory.createRootDeclaration(element.containingFile)
    CodeStyleManager.getInstance(project).performActionWithFormatterDisabled { element.replace(replacement) }
  }
}
