// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffInlayComponentsFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiffDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

@ApiStatus.Internal
class GitLabMergeRequestDiscussionInlayRenderer(cs: CoroutineScope,
                                                project: Project,
                                                vm: GitLabMergeRequestDiffDiscussionViewModel,
                                                avatarIconsProvider: IconsProvider<GitLabUserDTO>)
  : CodeReviewComponentInlayRenderer(
  GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(project, cs, avatarIconsProvider, vm)
)

@ApiStatus.Internal
class GitLabMergeRequestNewDiscussionInlayRenderer(cs: CoroutineScope,
                                                   project: Project,
                                                   vm: NewGitLabNoteViewModel,
                                                   avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                                   onCancel: () -> Unit)
  : CodeReviewComponentInlayRenderer(
  GitLabMergeRequestDiffInlayComponentsFactory.createNewDiscussion(project, cs, avatarIconsProvider, vm, onCancel)
)