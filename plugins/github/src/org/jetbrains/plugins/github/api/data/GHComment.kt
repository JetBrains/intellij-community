// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import java.util.*

open class GHComment(id: String,
                     val author: GHActor?,
                     val bodyHTML: String,
                     val createdAt: Date)
  : GHNode(id)
