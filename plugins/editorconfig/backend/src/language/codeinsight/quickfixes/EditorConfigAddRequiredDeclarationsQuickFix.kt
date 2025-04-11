// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.quickfixes

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigTemplateUtil
import org.jetbrains.annotations.Nls

class EditorConfigAddRequiredDeclarationsQuickFix(
  missingDescriptors: List<EditorConfigDeclarationDescriptor>,
  private val id: String
) : LocalQuickFix {
  @SafeFieldForPreview
  private val missingKeys = missingDescriptors.mapNotNull {
    EditorConfigDescriptorUtil.getParentOfType<EditorConfigQualifiedKeyDescriptor>(it)
  }

  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("quickfix.declaration.add-required.description")

  override fun applyFix(project: Project, problemDescriptor: ProblemDescriptor) {
    val element = problemDescriptor.psiElement as? EditorConfigDescribableElement ?: return
    val section = element.section
    val option = element.option
    val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
    val template = EditorConfigTemplateUtil.buildFullTemplate(
      id,
      missingKeys,
      section,
      mapOf(id to element.text)
    )

    editor.caretModel.moveToOffset(option.textOffset + option.textLength)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    editor.selectionModel.removeSelection()

    TemplateManager.getInstance(project).startTemplate(editor, template)
  }
}
