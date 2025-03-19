// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.json

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.extensions.isGithubActionFile
import org.jetbrains.plugins.github.extensions.isGithubWorkflowFile
import org.jetbrains.plugins.github.i18n.GithubBundle

private const val ACTION_SCHEMA_PATH = "/schemas/github-action.json"
private const val WORKFLOW_SCHEMA_PATH = "/schemas/github-workflow.json"

internal class GHActionJsonSchemaProviderFactory: JsonSchemaProviderFactory {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider?> {
    return listOf(ActionJsonSchemaProvider(), WorkflowJsonSchemaProvider())
  }

  private class ActionJsonSchemaProvider: JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean = isGithubActionFile(file)

    override fun getName(): @Nls String = GithubBundle.message("github.actions.action.file.json.schema")

    override fun getSchemaFile(): VirtualFile? = javaClass.getResource(ACTION_SCHEMA_PATH)?.let(VfsUtil::findFileByURL)

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
  }

  private class WorkflowJsonSchemaProvider: JsonSchemaFileProvider {
    override fun isAvailable(file: VirtualFile): Boolean = isGithubWorkflowFile(file)

    override fun getName(): @Nls String = GithubBundle.message("github.actions.workflow.file.json.schema")

    override fun getSchemaFile(): VirtualFile? = javaClass.getResource(WORKFLOW_SCHEMA_PATH)?.let(VfsUtil::findFileByURL)

    override fun getSchemaType(): SchemaType = SchemaType.embeddedSchema
  }
}