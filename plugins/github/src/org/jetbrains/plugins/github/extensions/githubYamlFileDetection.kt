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

val GITHUB_ACTION_SCHEMA_NAMES: Set<String> = setOf("github-action")
val GITHUB_WORKFLOW_SCHEMA_NAMES: Set<String> = setOf("github-workflow")

fun isGithubActionsFile(virtualFile: VirtualFile, project: Project?): Boolean {
  if (project == null) return false
  val isYamlFile = FileTypeRegistry.getInstance().isFileOfType(virtualFile, YAMLFileType.YML)
  if (!isYamlFile) return false
  return PsiManager.getInstance(project).findFile(virtualFile)?.let { isGithubActionsFile(it) } == true
}

fun isGithubActionsFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      matchesDefaultFilePath(psiFile, githubActionsFilePattern, githubWorkflowsFilePattern)
      || isGithubSchemaAssigned(psiFile, GITHUB_WORKFLOW_SCHEMA_NAMES + GITHUB_ACTION_SCHEMA_NAMES),
      psiFile
    )
  }
}

fun isGithubActionFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      matchesDefaultFilePath(psiFile, githubActionsFilePattern) || isGithubSchemaAssigned(psiFile, GITHUB_ACTION_SCHEMA_NAMES),
      psiFile
    )
  }
}

fun isGithubWorkflowFile(psiFile: PsiFile?): Boolean {
  if (psiFile == null) return false
  return CachedValuesManager.getCachedValue(psiFile) {
    CachedValueProvider.Result.create(
      matchesDefaultFilePath(psiFile, githubWorkflowsFilePattern) || isGithubSchemaAssigned(psiFile, GITHUB_WORKFLOW_SCHEMA_NAMES),
      psiFile
    )
  }
}

private fun isGithubSchemaAssigned(psiFile: PsiFile, schemaNames: Set<String>): Boolean {
  val project = psiFile.project
  val virtualFile = psiFile.originalFile.virtualFile
  if (virtualFile == null) return false
  val schemaFiles = project.service<JsonSchemaService>().getSchemaFilesForFile(virtualFile)
  val schemaAssigned = schemaFiles.any { schemaNames.contains(it.nameWithoutExtension) }
  return schemaAssigned
}

private fun matchesDefaultFilePath(psiFile: PsiFile, vararg pattern: Regex): Boolean {
  val virtualFile = psiFile.originalFile.virtualFile
  if (virtualFile == null) return false
  return (psiFile.language.`is`(YAMLLanguage.INSTANCE)
          && pattern.any { it.matches(StringUtil.newBombedCharSequence(virtualFile.path, PARSE_DELAY)) })
}

private val githubActionsFilePattern =
  Regex("""^.*/\.github/.*/action\.ya?ml${'$'}""")

private val githubWorkflowsFilePattern =
  Regex("""^.*/\.github/workflows/.*\.(ya?ml)${'$'}""")
