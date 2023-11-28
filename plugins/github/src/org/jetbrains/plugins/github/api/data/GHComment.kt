// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.intellij.collaboration.api.dto.GraphQLFragment
import java.util.*

@GraphQLFragment("/graphql/fragment/comment.graphql")
open class GHComment(id: String,
                     open val author: GHActor?,
                     open val body: String,
                     open val createdAt: Date)
  : GHNode(id)