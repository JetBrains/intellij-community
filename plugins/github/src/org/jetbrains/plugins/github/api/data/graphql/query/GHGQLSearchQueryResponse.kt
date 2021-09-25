// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql.query

import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO

open class GHGQLSearchQueryResponse<T>(val search: SearchConnection<T>)
  : GraphQLPagedResponseDataDTO<T> {

  override val pageInfo = search.pageInfo
  override val nodes = search.nodes

  class SearchConnection<T>(val pageInfo: GraphQLCursorPageInfoDTO, val nodes: List<T>)
}