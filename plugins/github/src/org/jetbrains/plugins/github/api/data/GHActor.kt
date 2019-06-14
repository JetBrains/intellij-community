// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHActor::class)
@JsonSubTypes(
  JsonSubTypes.Type(name = "User", value = GHUser::class)
)
open class GHActor(id: String,
                   val login: String,
                   val url: String,
                   val avatarUrl: String)
  : GHNode(id)