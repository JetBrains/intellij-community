// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LeakingThis")

package org.jetbrains.plugins.gitlab.mergerequest.file

import com.intellij.collaboration.file.ComplexPathVirtualFileWithoutContent
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnectionManager
import org.jetbrains.plugins.gitlab.api.GitLabProjectCoordinates
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectUIContext
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectUIContextHolder

abstract class GitLabProjectVirtualFile(override val connectionId: String,
                                        val project: Project,
                                        val glProject: GitLabProjectCoordinates)
  : ComplexPathVirtualFileWithoutContent(connectionId), GitLabVirtualFile {

  override fun getFileSystem(): ComplexPathVirtualFileSystem<*> = GitLabVirtualFileSystem.getInstance()

  override fun isValid(): Boolean = findContext() != null

  fun findContext(): GitLabProjectUIContext? = project.serviceIfCreated<GitLabProjectUIContextHolder>()
    ?.projectContext?.value?.takeIf { it.connectionId == connectionId }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabProjectVirtualFile) return false
    if (!super.equals(other)) return false

    if (project != other.project) return false
    return glProject == other.glProject
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + project.hashCode()
    result = 31 * result + glProject.hashCode()
    return result
  }
}
