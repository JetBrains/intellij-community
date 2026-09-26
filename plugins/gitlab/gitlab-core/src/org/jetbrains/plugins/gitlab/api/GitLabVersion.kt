// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import org.jetbrains.annotations.NonNls

data class GitLabVersion(
  val major: Int,
  val minor: Int? = null,
  val patch: Int? = null,
  val original: String? = null
) : Comparable<GitLabVersion> {
  // Compare versions per major version, then minor, then patch.
  override fun compareTo(other: GitLabVersion): Int =
    major.compareTo(other.major).takeIf { it != 0 }
    ?: (minor ?: 0).compareTo(other.minor ?: 0).takeIf { it != 0 }
    ?: (patch ?: 0).compareTo(other.patch ?: 0)

  override fun toString(): String =
    original ?: ("$major" + when (minor) {
      null -> ""
      else -> ".$minor" + when (patch) {
        null -> ""
        else -> ".$patch"
      }
    })

  companion object {
    /**
     * @throws IllegalArgumentException when the version from the dto cannot be parsed.
     */
    fun fromString(version: @NonNls String): GitLabVersion {
      val parts = version.split('.')

      // A version string has at least major version and at most up until patch
      require(parts.size <= 3) { "Version has too many parts: ${parts.size}, expected at most 3" }

      // Version parts are integers
      val intParts = parts.map {
        val intPart = it.takeWhile { c -> c.isDigit() }.toIntOrNull()
        requireNotNull(intPart) { "Cannot parse version parts" }
        intPart
      }

      return GitLabVersion(intParts[0], intParts.getOrNull(1) ?: 0, intParts.getOrNull(2) ?: 0, version)
    }
  }
}