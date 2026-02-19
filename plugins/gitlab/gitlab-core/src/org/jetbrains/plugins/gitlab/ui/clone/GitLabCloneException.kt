// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.clone

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccount

internal sealed interface GitLabCloneException {
  val account: GitLabAccount

  data class MissingAccessToken(override val account: GitLabAccount) : GitLabCloneException
  data class RevokedToken(override val account: GitLabAccount) : GitLabCloneException
  data class ConnectionError(override val account: GitLabAccount) : GitLabCloneException
  data class Unknown(override val account: GitLabAccount, val message: @Nls String) : GitLabCloneException
}