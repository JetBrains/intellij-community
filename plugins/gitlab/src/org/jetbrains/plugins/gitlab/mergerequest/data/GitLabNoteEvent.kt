// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

sealed interface GitLabNoteEvent<N> {
  class Added<N>(val note: N) : GitLabNoteEvent<N>
  class Changed<N>(val notes: List<N>) : GitLabNoteEvent<N>
  class Deleted<N>(val noteId: String) : GitLabNoteEvent<N>
  class AllDeleted<N> : GitLabNoteEvent<N>
}