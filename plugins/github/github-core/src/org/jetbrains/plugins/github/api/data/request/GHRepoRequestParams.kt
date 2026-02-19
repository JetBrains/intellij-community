// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request

/**
 * Additional params for [`GET /user/repos`](https://developer.github.com/v3/repos/#list-your-repositories) request
 *
 * Defaults to 'all' if not specified.
 */
enum class Type(val value: String) {
  ALL("all"),
  OWNER("owner"),
  PUBLIC("public"),
  PRIVATE("private"),
  MEMBER("member");

  companion object {
    const val KEY: String = "type"
  }
}

/**
 * Defaults to 'all' if not specified.
 */
enum class Visibility(val value: String) {
  ALL("all"),
  PUBLIC("public"),
  PRIVATE("private");

  companion object {
    const val KEY: String = "visibility"
  }
}

/**
 * Defaults to 'owner,collaborator,organization_member' if not specified.
 */
enum class Affiliation(val value: String) {
  OWNER("owner"),
  COLLABORATOR("collaborator"),
  @Suppress("unused") // API
  ORG_MEMBER("organization_member");

  companion object {
    const val KEY: String = "affiliation"

    val ALL: Set<Affiliation> = entries.toSet()

    fun combine(affiliations: Set<Affiliation>): String =
      affiliations.joinToString(",") { it.value }

    fun combine(vararg affiliations: Affiliation): String =
      combine(affiliations.toSet())
  }
}
