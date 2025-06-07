// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.diff.GitLabMergeRequestDiffInlayComponentsFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.util.GitLabStatistics

@ApiStatus.Internal
class GitLabMergeRequestDiscussionInlayRenderer internal constructor(cs: CoroutineScope,
                                                                     project: Project,
                                                                     vm: GitLabMergeRequestDiscussionViewModel,
                                                                     avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                                                     place: GitLabStatistics.MergeRequestNoteActionPlace)
  : CodeReviewComponentInlayRenderer(
  GitLabMergeRequestDiffInlayComponentsFactory.createDiscussion(project, cs, avatarIconsProvider, vm, place)
)

@ApiStatus.Internal
class GitLabMergeRequestDraftNoteInlayRenderer internal constructor(cs: CoroutineScope,
                                                                    project: Project,
                                                                    vm: GitLabNoteViewModel,
                                                                    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                                                    place: GitLabStatistics.MergeRequestNoteActionPlace)
  : CodeReviewComponentInlayRenderer(
  GitLabMergeRequestDiffInlayComponentsFactory.createDraftNote(project, cs, avatarIconsProvider, vm, place)
)

@ApiStatus.Internal
class GitLabMergeRequestNewDiscussionInlayRenderer internal constructor(cs: CoroutineScope,
                                                                        project: Project,
                                                                        vm: NewGitLabNoteViewModel,
                                                                        avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                                                        place: GitLabStatistics.MergeRequestNoteActionPlace,
                                                                        onCancel: () -> Unit)
  : CodeReviewComponentInlayRenderer(
  GitLabMergeRequestDiffInlayComponentsFactory.createNewDiscussion(project, cs, avatarIconsProvider, vm, onCancel, place)
)