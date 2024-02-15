// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.extensions

import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.YAMLLanguage

const val PARSE_DELAY = 1000L


fun isGithubActionsFile(virtualFile: VirtualFile, project: Project?): Boolean {
  if (project == null) return false
  val isYamlFile = FileTypeRegistry.getInstance().isFileOfType(virtualFile, YAMLFileType.YML)
  return isYamlFile && isGithubActionsFile(PsiManager.getInstance(project).findFile(virtualFile) ?: return false)
}

fun isGithubActionsFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      isGithubActionFile(psiFile) || isGithubWorkflowFile(psiFile),
      psiFile.manager.modificationTracker.forLanguage(YAMLLanguage.INSTANCE)
    )
  }
}

fun isGithubActionFile(psiFile: PsiFile): Boolean {
  return isProperActionFile(psiFile, githubActionsFilePattern, GITHUB_ACTION_SCHEMA_NAMES)
}

fun isGithubWorkflowFile(psiFile: PsiFile): Boolean {
  return isProperActionFile(psiFile, githubWorkflowsFilePattern, GITHUB_WORKFLOW_SCHEMA_NAMES)
}

private fun isGithubSchemaAssigned(project: Project?, virtualFile: VirtualFile, schemaNames: Set<String>): Boolean {
  if (project == null) return false
  val schemaFiles = project.service<JsonSchemaService>().getSchemaFilesForFile(virtualFile)
  val schemaAssigned = schemaFiles.any { schemaNames.contains(it.nameWithoutExtension) }
  return schemaAssigned
}

private fun isProperActionFile(psiFile: PsiFile, pattern: Regex, fileNames: Set<String>): Boolean {
  val virtualFile = psiFile.originalFile.virtualFile
  if (virtualFile == null) return false
  val schemaAssigned = isGithubSchemaAssigned(psiFile.project, virtualFile, fileNames)
  return (psiFile.language.`is` (YAMLLanguage.INSTANCE)
          && pattern.matches(StringUtil.newBombedCharSequence(virtualFile.path, PARSE_DELAY))) || schemaAssigned
}

private val githubActionsFilePattern =
  Regex("""^.*/\.github/.*/action\.ya?ml${'$'}""")

private val githubWorkflowsFilePattern =
  Regex("""^.*/\.github/workflows/.*\.(ya?ml)${'$'}""")
