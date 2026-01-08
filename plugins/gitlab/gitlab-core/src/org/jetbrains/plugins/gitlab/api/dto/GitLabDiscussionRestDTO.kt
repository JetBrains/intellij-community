// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.gitlab.api.GitLabRestId
import org.jetbrains.plugins.gitlab.api.GitLabRestIdData
import java.util.*

class GitLabDiscussionRestDTO(
  @JsonProperty("id")
  private val _id: String,
  val notes: List<GitLabNoteRestDTO>,
) {
  @JsonIgnore
  val id: GitLabRestId = GitLabRestIdData(_id)
  val createdAt: Date = notes.first().createdAt

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GitLabDiscussionRestDTO) return false

    if (_id != other._id) return false
    return notes == other.notes
  }

  override fun hashCode(): Int {
    var result = _id.hashCode()
    result = 31 * result + notes.hashCode()
    return result
  }
}
