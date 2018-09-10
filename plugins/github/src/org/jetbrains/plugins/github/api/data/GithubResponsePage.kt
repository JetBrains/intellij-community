// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import org.jetbrains.plugins.github.exceptions.GithubConfusingException

class GithubResponsePage<T> constructor(val items: List<T>,
                                        val firstLink: String? = null,
                                        val prevLink: String? = null,
                                        val nextLink: String? = null,
                                        val lastLink: String? = null) {

  companion object {
    const val HEADER_NAME = "Link"

    private val HEADER_SECTION_REGEX = Regex("""^<(.*)>; rel="(first|prev|next|last)"$""")

    //<https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15>; rel="next", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34>; rel="last", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1>; rel="first", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=13>; rel="prev"
    @JvmStatic
    @Throws(GithubConfusingException::class)
    fun <T> parseFromHeader(items: List<T>, linkHeaderValue: String?): GithubResponsePage<T> {
      if (linkHeaderValue == null) return GithubResponsePage(items)

      var firstLink: String? = null
      var prevLink: String? = null
      var nextLink: String? = null
      var lastLink: String? = null

      val split = linkHeaderValue.split(", ")
      if (split.isEmpty()) throw GithubConfusingException("Can't determine total items count from header: $linkHeaderValue")
      for (section in split) {
        val matchResult = HEADER_SECTION_REGEX.matchEntire(section) ?: continue
        val groupValues = matchResult.groupValues
        if (groupValues.size == 3) {
          when (groupValues[2]) {
            "first" -> firstLink = groupValues[1]
            "prev" -> prevLink = groupValues[1]
            "next" -> nextLink = groupValues[1]
            "last" -> lastLink = groupValues[1]
          }
        }
      }

      return GithubResponsePage(items, firstLink, prevLink, nextLink, lastLink)
    }
  }
}


