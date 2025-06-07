// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiFile

fun isGithubActionsFile(psiFile: PsiFile?): Boolean {
  val virtualFile = psiFile?.originalFile?.virtualFile ?: return false
  return isGithubActionsFile(virtualFile)
}

fun isGithubActionsFile(virtualFile: VirtualFile): Boolean {
  return isGithubActionYamlFile(virtualFile) || isGithubWorkflowYamlFile(virtualFile)
}

fun isGithubActionFile(virtualFile: VirtualFile?): Boolean {
  virtualFile ?: return false
  return isGithubActionYamlFile(virtualFile)
}


fun isGithubWorkflowFile(psiFile: PsiFile?): Boolean {
  val virtualFile = psiFile?.originalFile?.virtualFile ?: return false
  return isGithubWorkflowYamlFile(virtualFile)
}

fun isGithubWorkflowFile(virtualFile: VirtualFile?): Boolean {
  virtualFile ?: return false
  return isGithubWorkflowYamlFile(virtualFile)
}

private fun isGithubActionYamlFile(virtualFile: VirtualFile): Boolean {
  val fileName = virtualFile.name
  return virtualFile.isFile
         && (FileUtilRt.extensionEquals(fileName, "yml") || FileUtilRt.extensionEquals(fileName, "yaml"))
         && virtualFile.nameWithoutExtension == "action"
}

private fun isGithubWorkflowYamlFile(virtualFile: VirtualFile): Boolean {
  val fileName = virtualFile.name
  val filePath = virtualFile.path
  val workflowDirIndex = filePath.indexOf("/workflows")
  val githubDirIndex = filePath.indexOf(".github/")
  return virtualFile.isFile
         && (FileUtilRt.extensionEquals(fileName, "yml") || FileUtilRt.extensionEquals(fileName, "yaml"))
         && workflowDirIndex != -1
         && githubDirIndex != -1
         && workflowDirIndex > githubDirIndex
}
