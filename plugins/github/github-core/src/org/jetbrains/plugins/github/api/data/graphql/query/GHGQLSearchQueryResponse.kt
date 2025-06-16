// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql.query

import com.fasterxml.jackson.annotation.JsonIgnore
import com.intellij.collaboration.api.dto.GraphQLConnectionDTO
import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO

open class GHGQLSearchQueryResponse<T>(val search: SearchConnection<T>)
  : GraphQLPagedResponseDataDTO<T> {

  @JsonIgnore
  override val pageInfo: GraphQLCursorPageInfoDTO = search.pageInfo

  @JsonIgnore
  override val nodes: List<T> = search.nodes

  class SearchConnection<T>(pageInfo: GraphQLCursorPageInfoDTO, nodes: List<T> = listOf())
    : GraphQLConnectionDTO<T>(pageInfo, nodes)
}