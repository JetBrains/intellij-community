// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer

@GraphQLFragment("/graphql/fragment/botInfo.graphql")
class GHBot(
  id: String,
  override val login: String,
  override val url: String,
  override val avatarUrl: String,
) : GHNode(id), GHActor, GHPullRequestRequestedReviewer {
  override val name: String? = null
  override val shortName: String = login
  override fun getPresentableName(): String = name ?: login
}
