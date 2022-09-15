// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.GHNode

@GraphQLFragment("/graphql/fragment/pullRequestReviewThreadShort.graphql")
open class GHPullRequestReviewThreadShort(
  id: String,
  val isResolved: Boolean,
  val isOutdated: Boolean
) : GHNode(id)