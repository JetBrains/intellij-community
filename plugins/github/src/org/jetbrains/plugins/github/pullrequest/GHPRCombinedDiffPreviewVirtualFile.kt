// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.diff.tools.combined.CombinedDiffModelBuilder
import com.intellij.diff.tools.combined.CombinedDiffModelImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

@Suppress("EqualsOrHashCode")
internal class GHPRCombinedDiffPreviewVirtualFile(sourceId: String,
                                                  fileManagerId: String,
                                                  project: Project,
                                                  repository: GHRepositoryCoordinates,
                                                  private val pullRequest: GHPRIdentifier) :
  GHPRCombinedDiffPreviewVirtualFileBase(sourceId, fileManagerId, project, repository),
  CombinedDiffModelBuilder {

  override fun getName() = "#${pullRequest.number}.diff"
  override fun getPresentableName() = GithubBundle.message("pull.request.diff.editor.title", pullRequest.number)

  override fun getPath(): String =
    (fileSystem as GHPRVirtualFileSystem).getPath(fileManagerId, project, repository, pullRequest, sourceId, true)

  override fun getPresentablePath() = "${repository.toUrl()}/pulls/${pullRequest.number}.diff"

  override fun createModel(id: String): CombinedDiffModelImpl {
    return project.service<GHPRCreateCombinedDiffModelProvider>().createCombinedDiffModel(repository, pullRequest)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRCombinedDiffPreviewVirtualFile) return false
    if (other.pullRequest != pullRequest) return false
    if (!super.equals(other)) return false
    return true
  }
}
