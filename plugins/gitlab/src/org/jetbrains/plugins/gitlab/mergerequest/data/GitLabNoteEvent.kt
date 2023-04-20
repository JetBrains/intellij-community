// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import org.jetbrains.plugins.gitlab.api.dto.GitLabNoteDTO

sealed interface GitLabNoteEvent {
  class NotesChanged(val notes: List<GitLabNoteDTO>) : GitLabNoteEvent
  class Deleted(val noteId: String) : GitLabNoteEvent
}