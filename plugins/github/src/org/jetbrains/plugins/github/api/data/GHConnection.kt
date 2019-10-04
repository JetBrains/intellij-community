// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import org.jetbrains.plugins.github.api.data.graphql.GHGQLPageInfo
import org.jetbrains.plugins.github.api.data.graphql.GHGQLPagedRequestResponse

open class GHConnection<out T>(override val pageInfo: GHGQLPageInfo, nodes: List<T>)
  : GHNodes<T>(nodes), GHGQLPagedRequestResponse<T>