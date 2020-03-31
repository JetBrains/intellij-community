// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

class GHCommit(id: String,
               oid: String,
               abbreviatedOid: String,
               val url: String,
               val messageHeadline: String,
               val messageHeadlineHTML: String,
               val messageBodyHTML: String,
               val author: GHGitActor?,
               val committer: GHGitActor?)
  : GHCommitHash(id, oid, abbreviatedOid)