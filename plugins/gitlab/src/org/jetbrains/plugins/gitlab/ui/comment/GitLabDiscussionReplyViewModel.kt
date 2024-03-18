// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.async.modelFlow
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion

interface GitLabDiscussionReplyViewModel {

  val newNoteVm: Flow<NewGitLabNoteViewModel?>

  fun startWriting()
  fun stopWriting()
}

class GitLabDiscussionReplyViewModelImpl(
  parentCs: CoroutineScope,
  project: Project,
  currentUser: GitLabUserDTO,
  discussion: GitLabDiscussion
) : GitLabDiscussionReplyViewModel {

  private val cs = parentCs.childScope()

  private val isWriting = MutableStateFlow(false)
  override val newNoteVm: Flow<NewGitLabNoteViewModel?> = isWriting.mapScoped {
    if (!it) return@mapScoped null
    val cs = this
    GitLabNoteEditingViewModel.forReplyNote(cs, project, discussion, currentUser).apply {
      onDoneIn(cs) {
        text.value = ""
      }
      requestFocus()
    }
  }.modelFlow(cs, thisLogger())

  override fun startWriting() {
    isWriting.value = true
  }

  override fun stopWriting() {
    isWriting.value = false
  }
}