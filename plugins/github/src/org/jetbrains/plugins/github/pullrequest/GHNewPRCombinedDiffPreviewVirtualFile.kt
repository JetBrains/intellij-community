// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle

@Suppress("EqualsOrHashCode")
internal class GHNewPRCombinedDiffPreviewVirtualFile(sourceId: String,
                                                     fileManagerId: String,
                                                     project: Project,
                                                     repository: GHRepositoryCoordinates) :
  GHPRCombinedDiffPreviewVirtualFileBase(sourceId, fileManagerId, project, repository) {

  override fun getName() = "newPR.diff"
  override fun getPresentableName() = GithubBundle.message("pull.request.new.diff.editor.title")

  override fun getPath(): String = (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, null, sourceId, true)
  override fun getPresentablePath() = "${repository.toUrl()}/pulls/newPR.diff"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHNewPRCombinedDiffPreviewVirtualFile) return false
    if (!super.equals(other)) return false
    return true
  }
}
