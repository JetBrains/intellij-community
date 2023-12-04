// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("/graphql/fragment/checkRun.graphql")
class GHCommitCheckRunStatus(
  val name: String,
  val conclusion: GHCommitCheckSuiteConclusion?,
  val isRequired: Boolean,
  val url: String
)