// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonProperty

class GHCommit(id: String,
               oid: String,
               abbreviatedOid: String,
               url: String,
               val messageHeadline: String,
               messageHeadlineHTML: String,
               val messageBodyHTML: String,
               author: GHGitActor?,
               val committer: GHGitActor?,
               @JsonProperty("parents") parents: GHNodes<GHCommitHash>)
  : GHCommitShort(id, oid, abbreviatedOid, url, messageHeadlineHTML, author) {

  val parents = parents.nodes
}