// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

data class GHRepositoryCoordinates(val serverPath: GithubServerPath, val repositoryPath: GHRepositoryPath) {
  fun toUrl(): String {
    return serverPath.toUrl() + "/" + repositoryPath
  }

  override fun toString(): String {
    return "$serverPath/$repositoryPath"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHRepositoryCoordinates) return false

    if (!serverPath.equals(other.serverPath, true)) return false
    if (repositoryPath != other.repositoryPath) return false

    return true
  }

  override fun hashCode(): Int {
    var result = serverPath.hashCode()
    result = 31 * result + repositoryPath.hashCode()
    return result
  }
}
