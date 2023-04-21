// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

interface GitLabNotesContainer {
  val canAddNotes: Boolean

  suspend fun addNote(body: String)
}