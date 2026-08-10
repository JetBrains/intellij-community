// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.api.data.GHGitActor
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import java.util.Date

@ApiStatus.Internal
class GHCommitModel(
  project: Project,
  commitDTO: GHCommit,
) {
  val id: String = commitDTO.id
  val oid: String = commitDTO.oid
  val abbreviatedOid: String = commitDTO.abbreviatedOid
  val url: String = commitDTO.url
  @Language("HTML")
  val messageHeadline: @NlsSafe String = commitDTO.messageHeadline.convertToHtml(project)
  @Language("HTML")
  val messageBody: @NlsSafe String = commitDTO.messageBody.convertToHtml(project)
  val author: GHGitActor? = commitDTO.author
  val committer: GHGitActor? = commitDTO.committer
  val committedDate: Date = commitDTO.committedDate
  val parents: List<GHCommitHash> = commitDTO.parents
}
