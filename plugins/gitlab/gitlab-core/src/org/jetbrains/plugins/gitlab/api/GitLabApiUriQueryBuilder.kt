// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.collaboration.util.withQuery
import java.net.URI
import java.net.URLEncoder

/**
 * DSL builder for constructing GitLab API URI query strings with support for nested objects and arrays.
 *
 * Usage:
 * ```
 * val params = queryParams {
 *     "key" eq "value"
 *     "ids" eq listOf(1, 2, 3)
 *     "position" {
 *         "base_sha" eq sha
 *         "line_range" {
 *             "start" {
 *                 "line_code" eq code
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Generates:
 * - Flat parameters: `key=value`
 * - Arrays: `ids[]=1&ids[]=2&ids[]=3`
 * - Nested objects: `position[base_sha]=sha&position[line_range][start][line_code]=code`
 */
internal class GitLabApiUriQueryBuilder {
  private val params = mutableListOf<Pair<String, String>>()
  private val pathStack = mutableListOf<String>()

  /**
   * Sets a string parameter. Null values are automatically skipped.
   */
  infix fun String.eq(value: String?) {
    if (value == null) return
    params.add(buildKey(this) to value)
  }

  /**
   * Sets an integer parameter. Null values are automatically skipped.
   */
  infix fun String.eq(value: Int?) {
    if (value == null) return
    params.add(buildKey(this) to value.toString())
  }

  /**
   * Sets a boolean parameter. Null values are automatically skipped.
   */
  infix fun String.eq(value: Boolean?) {
    if (value == null) return
    params.add(buildKey(this) to value.toString())
  }

  /**
   * Sets an array parameter. Each value in the list generates a separate `key[]=value` entry.
   * Empty lists are skipped.
   */
  infix fun String.eq(values: List<Any>?) {
    if (values == null) return
    val baseKey = buildKey(this) + "[]"
    if (values.isEmpty()) {
      params.add(baseKey to "")
    }
    else {
      values.forEach { value ->
        params.add(baseKey to value.toString())
      }
    }
  }

  /**
   * Creates a nested object. The block executes with the key pushed onto the path stack,
   * generating parameters with bracket notation (e.g., `parent[child]=value`).
   */
  operator fun String.invoke(block: GitLabApiUriQueryBuilder.() -> Unit) {
    pathStack.add(this)
    this@GitLabApiUriQueryBuilder.block()
    pathStack.removeLast()
  }

  private fun buildKey(key: String): String {
    return if (pathStack.isEmpty()) {
      key
    }
    else {
      buildString {
        append(pathStack.first())
        pathStack.drop(1).forEach {
          append("[$it]")
        }
        append("[$key]")
      }
    }
  }

  /**
   * Builds the final query string with URL-encoded values.
   * Parameters are joined with `&`.
   */
  fun build(): String {
    return params.joinToString("&") { (key, value) ->
      "$key=${URLEncoder.encode(value, Charsets.UTF_8)}"
    }
  }

  companion object {
    /**
     * Creates a query parameters string using the DSL.
     *
     * Example:
     * ```
     * val query = queryParams {
     *     "note" eq body
     *     "reviewer_ids" eq listOf(1, 2, 3)
     *     "position" {
     *         "base_sha" eq sha
     *         "head_sha" eq headSha
     *     }
     * }.build()
     * ```
     */
    fun build(block: GitLabApiUriQueryBuilder.() -> Unit): String =
      GitLabApiUriQueryBuilder().apply(block).build()
  }
}


/**
 * Extension function to add query parameters to a URI using the DSL builder.
 *
 * Example:
 * ```
 * val uri = baseUri.withQueryBuilder {
 *   "key" eq "value"
 *   "position" {
 *     "base_sha" eq sha
 *   }
 * }
 * ```
 */
internal fun URI.withQuery(queryBuilder: GitLabApiUriQueryBuilder.() -> Unit): URI =
  withQuery(GitLabApiUriQueryBuilder.build(queryBuilder))