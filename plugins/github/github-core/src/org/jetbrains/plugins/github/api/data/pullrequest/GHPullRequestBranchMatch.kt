// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

@GraphQLFragment("/graphql/fragment/pullRequestBranchMatch.graphql")
class GHPullRequestBranchMatch(
  id: String,
  val number: Long,
  val title: String?,
  val headRefName: String,
) : GHNode(id)

fun GHPullRequestBranchMatch.toPRIdentifier(): GHPRIdentifier = GHPRIdentifier(id, number)
