// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api

data class GithubRepositoryPath(private val serverPath: GithubServerPath, private val repositoryPath: GithubFullPath) {
  override fun toString(): String {
    return "$serverPath/${repositoryPath.fullName}"
  }
}
