// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.editorconfig.language.messages.EditorConfigBundle
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigDeclarationDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigQualifiedKeyDescriptor
import org.editorconfig.language.services.EditorConfigOptionDescriptorManager
import org.editorconfig.language.util.EditorConfigDescriptorUtil
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.EditorConfigTemplateUtil
import org.jetbrains.annotations.Nls

class EditorConfigAddDeclarationPartIntention : IntentionAction {
  override fun getText(): @Nls String = EditorConfigBundle.get("intention.add-declaration-part")
  override fun getFamilyName(): @Nls String = EditorConfigBundle.get("intention.add-declaration-part")
  override fun startInWriteAction(): Boolean = true

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
    findDeclaration(EditorConfigPsiTreeUtil.findIdentifierUnderCaret(editor, file)) != null

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val element = findDeclaration(EditorConfigPsiTreeUtil.findIdentifierUnderCaret(editor, file)) ?: return
    val section = element.section
    val option = element.option
    val descriptor = element.getDescriptor(false) as? EditorConfigDeclarationDescriptor ?: return
    val declarationDescriptors = EditorConfigOptionDescriptorManager.getInstance(project)
      .getDeclarationDescriptors(descriptor.id)
      .mapNotNull { EditorConfigDescriptorUtil.getParentOfType(it, EditorConfigQualifiedKeyDescriptor::class) }


    val template = EditorConfigTemplateUtil.buildTemplate(
      descriptor.id,
      declarationDescriptors,
      section,
      mapOf(descriptor.id to element.text)
    )

    editor.caretModel.moveToOffset(option.textOffset + option.textLength)
    editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    editor.selectionModel.removeSelection()

    TemplateManager.getInstance(project).startTemplate(editor, template)
  }

  private tailrec fun findDeclaration(element: PsiElement?): EditorConfigDescribableElement? {
    element ?: return null
    if (element is EditorConfigDescribableElement
        && element.getDescriptor(false) is EditorConfigDeclarationDescriptor) {
      return element
    }
    return findDeclaration(element.parent)
  }
}
