// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("graphql/fragment/refUpdateRule.graphql")
data class GHRefUpdateRule(
  //Can this branch be deleted.
  val allowsDeletions: Boolean,
  //Are force pushes allowed on this branch.
  val allowsForcePushes: Boolean,
  //Identifies the protection rule pattern.
  val pattern: String,
  //Number of approving reviews required to update matching branches.
  val requiredApprovingReviewCount: Int?,
  //List of required status check contexts that must pass for commits to be accepted to matching branches.
  val requiredStatusCheckContexts: List<String?>,
  //Are merge commits prohibited from being pushed to this branch.
  val requiresLinearHistory: Boolean,
  //Are commits required to be signed.
  val requiresSignatures: Boolean,
  //Can the viewer push to the branch
  val viewerCanPush: Boolean
)