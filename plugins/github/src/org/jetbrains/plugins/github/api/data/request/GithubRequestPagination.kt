// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request

class GithubRequestPagination @JvmOverloads constructor(val pageNumber: Int = 1, val pageSize: Int = DEFAULT_PAGE_SIZE) {
  override fun toString(): String {
    return "page=$pageNumber&per_page=$pageSize"
  }

  companion object {
    /**
     * Max page size
     */
    const val DEFAULT_PAGE_SIZE = 100

    val DEFAULT = GithubRequestPagination()
  }
}
