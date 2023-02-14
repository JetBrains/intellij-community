// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.collaboration.api.ServerPath
import git4idea.remote.GitRemoteUrlCoordinates
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface HostedRepositoryCoordinates {
  val serverPath: ServerPath
}

@ApiStatus.Experimental
interface HostedGitRepositoryMapping {
  val repository: HostedRepositoryCoordinates
  val remote: GitRemoteUrlCoordinates
}
