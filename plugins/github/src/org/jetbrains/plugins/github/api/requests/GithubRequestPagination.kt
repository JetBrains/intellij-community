// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.requests

class GithubRequestPagination @JvmOverloads constructor(val pageNumber: Int = 1, val pageSize: Int = DEFAULT_PAGE_SIZE) {
  override fun toString(): String {
    return "page=$pageNumber&per_page=$pageSize"
  }

  companion object {
    const val DEFAULT_PAGE_SIZE = 100
  }
}
