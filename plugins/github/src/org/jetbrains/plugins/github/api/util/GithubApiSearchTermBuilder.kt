// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util

@DslMarker
private annotation class SearchTermDsl

@SearchTermDsl
class GithubApiSearchTermBuilder {
  private val builder = StringBuilder()

  fun qualifier(name: String, value: String?) {
    if (value != null) append("$name:$value")
  }

  fun query(value: String?) {
    if (value != null) append(value)
  }

  private fun append(part: String) {
    if (builder.isNotEmpty()) builder.append("+")
    builder.append(part)
  }

  companion object {
    @JvmStatic
    fun searchQuery(init: GithubApiSearchTermBuilder.() -> Unit): String {
      val query = GithubApiSearchTermBuilder()
      init(query)
      return query.builder.toString()
    }
  }
}