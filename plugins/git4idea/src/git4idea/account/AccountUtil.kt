// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.account

import com.intellij.collaboration.auth.ServerAccount
import git4idea.ui.branch.GitRepositoryMappingData

@Deprecated("Use git4idea.push.GitPushNotificationUtil instead")
data class RepoAndAccount<M : GitRepositoryMappingData, A : ServerAccount>(
  val projectMapping: M,
  val account: A
)