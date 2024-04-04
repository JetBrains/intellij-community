// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

const val PARSE_DELAY = 1000L

fun isGithubActionsFile(virtualFile: VirtualFile, project: Project?): Boolean {
  if (project == null) return false
  return PsiManager.getInstance(project).findFile(virtualFile)?.let { isGithubActionsFile(it) } == true
}

fun isGithubActionsFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      matchesDefaultFilePath(psiFile, githubActionsFilePattern, githubWorkflowsFilePattern),
      psiFile
    )
  }
}

fun isGithubActionFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      matchesDefaultFilePath(psiFile, githubActionsFilePattern),
      psiFile
    )
  }
}

fun isGithubWorkflowFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      matchesDefaultFilePath(psiFile, githubWorkflowsFilePattern),
      psiFile
    )
  }
}

private fun matchesDefaultFilePath(psiFile: PsiFile, vararg pattern: Regex): Boolean {
  val virtualFile = psiFile.originalFile.virtualFile
  if (virtualFile == null) return false
  return (pattern.any { it.matches(StringUtil.newBombedCharSequence(virtualFile.path, PARSE_DELAY)) })
}

val githubActionsFilePattern =
  Regex("""^.*(/|^)action\.ya?ml${'$'}""")

private val githubWorkflowsFilePattern =
  Regex("""^.*/\.github/workflows/.*\.(ya?ml)${'$'}""")
