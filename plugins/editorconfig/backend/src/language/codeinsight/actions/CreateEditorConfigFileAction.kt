// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.editorconfig.common.EditorConfigBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.CreateInDirectoryActionBase
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.editorconfig.language.filetype.EditorConfigFileConstants

class CreateEditorConfigFileAction : CreateInDirectoryActionBase(
  EditorConfigBundle.get("create.file.title"),
  EditorConfigBundle.get("create.file.description"),
  AllIcons.Nodes.Editorconfig
) {
  override fun actionPerformed(event: AnActionEvent) {
    val view = event.getData(LangDataKeys.IDE_VIEW) ?: return
    val directory = view.getOrChooseDirectory() ?: return
    val file = createOrFindEditorConfig(directory)
    view.selectElement(file)
  }

  private fun createOrFindEditorConfig(directory: PsiDirectory): PsiFile {
    val name = EditorConfigFileConstants.FILE_NAME
    val existing = directory.findFile(name)
    if (existing != null) return existing
    return runWriteAction { directory.createFile(".editorconfig") }
  }
}
