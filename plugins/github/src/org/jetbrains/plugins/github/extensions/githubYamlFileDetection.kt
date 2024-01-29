// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage

fun isGithubActionsFile(virtualFile: VirtualFile, project: Project?): Boolean {
  if (project == null) return false
  val isYamlFile = FileTypeRegistry.getInstance().isFileOfType(virtualFile, YAMLFileType.YML)
  return isYamlFile && isGithubActionsFile(PsiManager.getInstance(project).findFile(virtualFile) ?: return false)
}

fun isGithubActionsFile(psiFile: PsiFile?): Boolean {
  psiFile ?: return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      isGithubActionFileInner(psiFile) || isGithubWorkflowFileInner(psiFile),
      psiFile.manager.modificationTracker.forLanguage(YAMLLanguage.INSTANCE)
    )
  }
}

private fun isGithubActionFileInner(psiFile: PsiFile): Boolean {
  return githubActionsFilePattern.matchesFromLeafUp(psiFile) && psiFile.language.`is` (YAMLLanguage.INSTANCE)
}

private fun isGithubWorkflowFileInner(psiFile: PsiFile): Boolean {
  return githubWorkflowsFilePattern.matchesFromLeafUp(psiFile) && psiFile.language.`is` (YAMLLanguage.INSTANCE)
}

private val githubActionsFilePattern = RelativeFilePathPattern(
  FileSystemItemPattern("\\.github"),
  FileSystemItemPattern("actions"),
  FileSystemItemPattern("(.)+"),
  FileSystemItemPattern("action.ya?ml")
)

private val githubWorkflowsFilePattern = RelativeFilePathPattern(
  FileSystemItemPattern("\\.github"),
  FileSystemItemPattern("workflows"),
  FileSystemItemPattern("(.)+.ya?ml")
)


private class FileSystemItemPattern(vararg expectedNames: String) {
  private val expectedNames = expectedNames.toSet().map { Regex(StringUtil.newBombedCharSequence(it, 1000L).toString()) }

  fun matches(node: PsiFileSystemItem): Boolean {
    return expectedNames.all{ it.matches(node.name)}
  }
}

private class RelativeFilePathPattern(vararg expectedItems: FileSystemItemPattern) {
  private val expectedItems = expectedItems.reversed()

  fun matchesFromLeafUp(leafItem: PsiFileSystemItem?): Boolean {
    return expectedItems.asSequence()
      .zip(generateSequence(leafItem, PsiFileSystemItem::getParent))
      .map { (pattern, item) -> pattern.matches(item) }
      .all { it }
  }
}
