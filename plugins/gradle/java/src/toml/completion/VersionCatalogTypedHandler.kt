// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.toml.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.toml.navigation.refKeyValuePattern
import org.toml.lang.psi.TomlElementTypes
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKeyValue

@SuppressWarnings("unused")
class VersionCatalogTypedHandler : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (file !is TomlFile) return Result.CONTINUE
    if (charTyped != '"' && charTyped != ' ' && charTyped != '=') return Result.CONTINUE
    val element = file.findElementAt(editor.caretModel.offset)?.prevSibling?.asSafely<TomlKeyValue>() ?: return Result.CONTINUE
    if (element.children.filterIsInstance<LeafPsiElement>().find { it.elementType == TomlElementTypes.EQ } == null) {
      return Result.CONTINUE
    }
    if (refKeyValuePattern.accepts(element)) {
      AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    }
    return super.checkAutoPopup(charTyped, project, editor, file)
  }
}