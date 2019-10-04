// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql

class GHGQLPageInfo(val startCursor: String?, val hasPreviousPage: Boolean,
                    val endCursor: String?, val hasNextPage: Boolean)