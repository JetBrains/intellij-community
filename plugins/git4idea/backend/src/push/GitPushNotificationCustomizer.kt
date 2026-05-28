// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ProjectExtensionPointName
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface GitPushNotificationCustomizer {
  suspend fun getActions(
    repository: GitRepository,
    pushResult: GitPushRepoResult,
    customParams: Map<String, VcsPushOptionValue>
  ): List<AnAction>

  companion object {
    val EP_NAME = ProjectExtensionPointName<GitPushNotificationCustomizer>("Git4Idea.gitPushNotificationCustomizer")
  }
}

@get:Experimental
val GitPushRepoResult.isSuccessful: Boolean
  get() =
    type != GitPushRepoResult.Type.ERROR &&
    type != GitPushRepoResult.Type.REJECTED_NO_FF &&
    type != GitPushRepoResult.Type.REJECTED_STALE_INFO &&
    type != GitPushRepoResult.Type.REJECTED_OTHER