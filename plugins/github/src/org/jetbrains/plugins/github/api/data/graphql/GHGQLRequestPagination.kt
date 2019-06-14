// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql

import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination

class GHGQLRequestPagination @JvmOverloads constructor(val afterCursor: String? = null,
                                                       val pageSize: Int = GithubRequestPagination.DEFAULT_PAGE_SIZE) {
  override fun toString(): String {
    return "afterCursor=$afterCursor&per_page=$pageSize"
  }
}
