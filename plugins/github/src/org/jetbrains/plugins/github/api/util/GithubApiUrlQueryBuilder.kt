// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

import org.jetbrains.plugins.github.api.requests.GithubRequestPagination

@DslMarker
private annotation class UrlQueryDsl

@UrlQueryDsl
class GithubApiUrlQueryBuilder {
  private val builder = StringBuilder()

  fun param(name: String, value: String?) {
    if (value != null) append("$name=$value")
  }

  fun param(pagination: GithubRequestPagination?) {
    if (pagination != null) {
      param("page", pagination.pageNumber.toString())
      param("per_page", pagination.pageSize.toString())
    }
  }

  private fun append(part: String) {
    if (builder.isEmpty()) builder.append("?") else builder.append("&")
    builder.append(part)
  }

  companion object {
    @JvmStatic
    fun urlQuery(init: GithubApiUrlQueryBuilder.() -> Unit) : String {
      val query = GithubApiUrlQueryBuilder()
      init(query)
      return query.builder.toString()
    }
  }
}