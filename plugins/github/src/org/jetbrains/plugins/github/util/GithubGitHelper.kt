// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import org.jetbrains.plugins.github.api.GithubServerPath

/**
 * Utilities for Github-Git interactions
 */
class GithubGitHelper(private val githubSettings: GithubSettings) {

  fun getRemoteUrl(server: GithubServerPath, user: String, repo: String): String {
    return if (githubSettings.isCloneGitUsingSsh) {
      "git@${server.host}:${server.suffix?.substring(1).orEmpty()}/$user/$repo.git"
    }
    else {
      "https://${server.host}${server.suffix.orEmpty()}/$user/$repo.git"
    }
  }
}