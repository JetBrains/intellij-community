// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.actions.vectorAsset

import com.intellij.compose.ide.plugin.resources.getAllComposeResourcesDirs
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.VfsUtil

internal class ComposeResourcesNewVectorAsset : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val targetDir = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

    val dialog = ComposeResourcesVectorAssetDialog(project, targetDir)
    if (!dialog.showAndGet()) return
    val name = dialog.outputFileName ?: return
    val content = dialog.finalXmlContent ?: return
    val outputDir = dialog.outputDirectory

    WriteCommandAction.runWriteCommandAction(project, e.presentation.text, null, {
      val vfsDir = VfsUtil.createDirectoryIfMissing(outputDir.toAbsolutePath().toString()) ?: return@runWriteCommandAction
      val vfsFile = vfsDir.findOrCreateChildData(this, "$name.xml")
      VfsUtil.saveText(vfsFile, content)
    })
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
    val project = e.project
    if (file == null || !file.isDirectory || project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val module = ModuleUtilCore.findModuleForFile(file, project)
    val composeModuleNames = project.getAllComposeResourcesDirs().map { it.moduleName }

    e.presentation.isEnabledAndVisible = module != null && composeModuleNames.any { it in module.name }
  }
}
