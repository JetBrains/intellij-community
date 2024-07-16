// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiFile

fun isGithubActionsFile(psiFile: PsiFile?): Boolean {
  val virtualFile = psiFile?.originalFile?.virtualFile ?: return false
  return isGithubActionsFile(virtualFile)
}

fun isGithubActionsFile(virtualFile: VirtualFile): Boolean {
  return isGithubActionsActionFile(virtualFile) || isGithubWorkflowFile(virtualFile)
}

fun isGithubWorkflowFile(psiFile: PsiFile?): Boolean {
  val virtualFile = psiFile?.originalFile?.virtualFile ?: return false
  return isGithubWorkflowFile(virtualFile)
}

private fun isGithubActionsActionFile(virtualFile: VirtualFile): Boolean {
  val nameSequence = virtualFile.nameSequence
  return virtualFile.isFile
         && (nameSequence.endsWith(".yaml") || nameSequence.endsWith(".yml"))
         && virtualFile.nameWithoutExtension == "action"
}

private fun isGithubWorkflowFile(virtualFile: VirtualFile): Boolean {
  val nameSequence = virtualFile.nameSequence
  return virtualFile.isFile
         && (nameSequence.endsWith(".yaml") || nameSequence.endsWith(".yml"))
         && virtualFile.parent?.name == "workflows"
         && virtualFile.parent?.parent?.name == ".github"
}
