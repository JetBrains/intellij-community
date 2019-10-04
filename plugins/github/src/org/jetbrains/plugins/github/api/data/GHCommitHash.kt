// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

open class GHCommitHash(id: String,
                        val oid: String,
                        val abbreviatedOid: String)
  : GHNode(id)