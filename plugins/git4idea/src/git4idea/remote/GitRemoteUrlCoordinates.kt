// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote

import com.intellij.openapi.util.NlsSafe
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository

data class GitRemoteUrlCoordinates(@NlsSafe val url: String, val remote: GitRemote, val repository: GitRepository)