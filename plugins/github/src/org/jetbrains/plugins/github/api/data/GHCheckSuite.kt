// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLFragment

@GraphQLFragment("/graphql/fragment/checkSuite.graphql")
data class GHCheckSuite(
  val checkRuns: CheckRunConnection
) {
  class CheckRunConnection(
    pageInfo: GraphQLCursorPageInfoDTO,
    nodes: List<GHCheckRun>
  ) : GraphQLConnectionDTO<GHCheckRun>(pageInfo, nodes)
}

