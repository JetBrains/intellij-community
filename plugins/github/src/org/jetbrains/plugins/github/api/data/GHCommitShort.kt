// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

open class GHCommitShort(id: String,
                         oid: String,
                         abbreviatedOid: String,
                         val url: String,
                         val messageHeadlineHTML: String,
                         val author: GHGitActor?)
  : GHCommitHash(id, oid, abbreviatedOid) {
}
