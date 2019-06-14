// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql.query

import org.jetbrains.plugins.github.api.data.graphql.GHGQLPageInfo
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPagedRequestResponse

open class GHGQLSearchQueryResponse<T>(val search: SearchConnection<T>)
  : GHGQLPagedRequestResponse<T> {

  override val pageInfo = search.pageInfo
  override val nodes = search.nodes

  class SearchConnection<T>(val pageInfo: GHGQLPageInfo, val nodes: List<T>)
}