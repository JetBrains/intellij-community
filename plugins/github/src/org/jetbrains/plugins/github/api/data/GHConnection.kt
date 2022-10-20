// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLCursorPageInfoDTO
import com.intellij.collaboration.api.dto.GraphQLNodesDTO
import com.intellij.collaboration.api.dto.GraphQLPagedResponseDataDTO

open class GHConnection<out T>(override val pageInfo: GraphQLCursorPageInfoDTO, nodes: List<T>)
  : GraphQLNodesDTO<T>(nodes), GraphQLPagedResponseDataDTO<T>