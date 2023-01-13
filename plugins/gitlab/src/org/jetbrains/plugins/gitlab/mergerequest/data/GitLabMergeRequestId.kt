// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

interface GitLabMergeRequestId {
  val id: Long
  val iid: String

  data class Simple(override val id: Long, override val iid: String) : GitLabMergeRequestId {
    constructor(id: GitLabMergeRequestId) : this(id.id, id.iid)
  }
}
