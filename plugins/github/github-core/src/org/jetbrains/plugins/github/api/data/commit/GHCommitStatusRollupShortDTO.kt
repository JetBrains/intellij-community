// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit

import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState

@GraphQLFragment("/graphql/fragment/commitStatusRollupShort.graphql")
data class GHCommitStatusRollupShortDTO(
  val state: GHCommitStatusContextState
)