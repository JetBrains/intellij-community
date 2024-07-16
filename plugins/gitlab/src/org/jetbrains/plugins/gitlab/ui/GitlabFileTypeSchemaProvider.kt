// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

internal class GitlabFileTypeSchemaProvider : FileTypeUsageSchemaDescriptor {
  private val SCHEMA_KEY: Key<Boolean> = Key.create("GITLAB_CI_SCHEMA")

  override fun describes(project: Project, file: VirtualFile): Boolean {
    if (file.fileType.name != "YAML") return false

    file.getUserData(SCHEMA_KEY)?.let { return it }

    val isGithubFile = isGitlabCiFile(file)
    file.putUserData(SCHEMA_KEY, isGithubFile)
    return isGithubFile
  }
}