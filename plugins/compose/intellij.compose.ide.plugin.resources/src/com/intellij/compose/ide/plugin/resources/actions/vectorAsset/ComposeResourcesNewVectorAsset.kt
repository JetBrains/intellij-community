// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.compose.ide.plugin.resources.getAllComposeResourcesDirs
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files

internal class ComposeResourcesNewVectorAsset : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val targetDir = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

    val dialog = ComposeResourcesVectorAssetDialog(project, targetDir)
    if (dialog.showAndGet()) {
      dialog.filePath.ifBlank { return }
      val name = dialog.getOutputFileName()
      val content = dialog.finalXmlContent ?: return
      val outputDir = dialog.outputDirectory

      WriteCommandAction.runWriteCommandAction(project, e.presentation.text, null, {
        Files.createDirectories(outputDir)
        val target = outputDir.resolve("$name.xml")
        Files.writeString(target, content)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target)
      })
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val project = e.project

    if (file == null || project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val allDirs = project.getAllComposeResourcesDirs()
    val isComposeResource = allDirs.any { it.directoryPath.parent.parent.parent.toString() in file.path }

    e.presentation.isEnabledAndVisible = file.isDirectory && isComposeResource
  }
}
