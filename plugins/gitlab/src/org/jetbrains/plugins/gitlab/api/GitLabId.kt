// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

/**
 * Model-level class for representing the ID of some GitLab-owned object.
 * Objects on GitLab accessed through the REST API can only be referenced by
 * number IDs, whereas objects accessed through the GraphQL API are referenced
 * by global IDs of the form:
 * gid://gitlab/{type}/{REST ID}
 *
 * The form of GIDs is subject to change, so it's best to try not to translate
 * between REST IDs and GIDs without some object coming from GitLab explicitly.
 */
sealed interface GitLabId {
  val restId: String?
  val gid: String?

  /**
   * Unsafely guess a REST ID from the information known currently (any instance of
   * [GitLabId] will always have at least one ID to work with).
   *
   * Try to avoid this method as much as possible, as it's unsafe and truly a guess.
   * This method exists to track usages of such an unsafe conversion.
   *
   * TODO: Remove this when there is a fix for implicit conversions between IDs
   */
  fun guessRestId(): String =
    restId ?: gid?.substringAfterLast('/')!!

  /**
   * Unsafely guess a GraphQL ID from the information known currently (any instance of
   * [GitLabId] will always have at least one ID to work with).
   *
   * Try to avoid this method as much as possible, as it's unsafe and truly a guess.
   * This method exists to track usages of such an unsafe conversion.
   *
   * TODO: Remove this when there is a fix for implicit conversions between IDs
   *
   * @return `null` if no guess can be made, because information is definitely missing.
   * Some guess of the GID if a guess can be made.
   */
  fun guessGid(app: String = "gitlab"): String?
}

sealed interface GitLabRestId : GitLabId {
  override val restId: String
}

sealed interface GitLabGid : GitLabId {
  override val gid: String

  override fun guessGid(app: String): String = gid
}

data class GitLabRestIdData(override val restId: String,
                            private val gidDomain: String? = null) : GitLabRestId {
  override val gid: String? = null

  override fun guessGid(app: String): String? = gidDomain?.let { "gid://$app/$it/$restId" }
  override fun toString(): String = restId
}

data class GitLabGidData(override val gid: String) : GitLabGid {
  override val restId: String? = null

  override fun toString(): String = gid
}

data class GitLabBothIdData(
  override val restId: String,
  override val gid: String
) : GitLabRestId, GitLabGid {
  override fun toString(): String = gid
}
