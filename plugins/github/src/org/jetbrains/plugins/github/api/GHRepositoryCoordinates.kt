// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

data class GHRepositoryCoordinates(internal val serverPath: GithubServerPath, internal val repositoryPath: GHRepositoryPath) {
  fun toUrl(): String {
    return serverPath.toUrl() + "/" + repositoryPath
  }

  override fun toString(): String {
    return "$serverPath/$repositoryPath"
  }
}
