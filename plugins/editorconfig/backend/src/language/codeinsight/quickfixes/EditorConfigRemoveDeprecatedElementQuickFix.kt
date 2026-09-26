// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.MacroCallNode
import com.intellij.codeInsight.template.macro.CompleteMacro
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls

class EditorConfigRemoveDeprecatedElementQuickFix : LocalQuickFix {
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.deprecated.element.remove")
  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement as? EditorConfigDescribableElement ?: return
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val templateManager = TemplateManager.getInstance(project)
    val builder = TemplateBuilderImpl(element)
    builder.replaceElement(element, MacroCallNode(CompleteMacro()))

    runWriteAction {
      val template = builder.buildInlineTemplate()
      template.isToReformat = true
      templateManager.startTemplate(editor, template)
    }
  }
}
