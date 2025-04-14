// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.editorconfig.common.syntax.psi.EditorConfigDescribableElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor
import org.editorconfig.language.schema.descriptors.getDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigConstantDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnionDescriptor
import org.editorconfig.language.schema.descriptors.impl.EditorConfigUnsetValueDescriptor
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.EditorConfigTextMatchingUtil
import org.jetbrains.annotations.Nls

class EditorConfigInvertValueIntention : IntentionAction {
  override fun getText(): @Nls String = EditorConfigBundle.get("intention.invert-option-value")
  override fun getFamilyName(): String = EditorConfigBundle.get("intention.invert-option-value")
  override fun startInWriteAction(): Boolean = true

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    val value = getDescribableElement(editor, file) ?: return false
    return getInvertedValue(value) != null
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val optionValue = getDescribableElement(editor, file) ?: return
    val notValue = getInvertedValue(optionValue) ?: return
    val document = editor.document
    val textRange = optionValue.textRange
    document.replaceString(textRange.startOffset, textRange.endOffset, notValue)
  }

  private fun getInvertedValue(element: EditorConfigDescribableElement): String? {
    val descriptor = findUnionDescriptor(element.getDescriptor(true))
    return getInvertedValue(element.text, descriptor)
  }

  private fun getInvertedValue(value: String, union: EditorConfigUnionDescriptor?): String? {
    if (union?.children?.size != 2) return null
    val first = union.children[0]
    val second = union.children[1]
    return when {
      constantMatches(first, value) -> getText(second)
      constantMatches(second, value) -> getText(first)
      else -> null
    }
  }

  private fun getDescribableElement(editor: Editor, file: PsiFile): EditorConfigDescribableElement? =
    EditorConfigPsiTreeUtil.findIdentifierUnderCaret(editor, file)?.parentOfType(withSelf = true)

  private fun getText(descriptor: EditorConfigDescriptor): String? {
    if (descriptor !is EditorConfigConstantDescriptor) return null
    return descriptor.text
  }

  private fun constantMatches(descriptor: EditorConfigDescriptor, value: String): Boolean {
    if (descriptor !is EditorConfigConstantDescriptor) return false
    return EditorConfigTextMatchingUtil.textMatchesToIgnoreCase(descriptor.text, value)
  }

  private fun findUnionDescriptor(descriptor: EditorConfigDescriptor?): EditorConfigUnionDescriptor? {
    if (descriptor is EditorConfigUnsetValueDescriptor) return null
    return descriptor?.parent as? EditorConfigUnionDescriptor
  }
}
