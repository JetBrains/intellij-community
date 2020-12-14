// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.extensions

import com.intellij.icons.AllIcons
import com.intellij.ide.IconProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import javax.swing.Icon

class GithubYamlIconProvider : IconProvider() {

  companion object {
    private val GITHUB_SCHEMA_NAMES = setOf("github-workflow", "github-action")
  }

  override fun getIcon(element: PsiElement, flags: Int): Icon? {
    if (element !is PsiFile) return null
    val file = element.virtualFile ?: return null
    val project = element.project

    val schemaFiles = project.service<JsonSchemaService>().getSchemaFilesForFile(file)
    if (schemaFiles.any { GITHUB_SCHEMA_NAMES.contains(it.name) }) {
      return AllIcons.Vcs.Vendors.Github
    }

    return null
  }
}
