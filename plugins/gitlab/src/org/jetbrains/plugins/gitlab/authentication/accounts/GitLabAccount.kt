// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.auth.ServerAccount
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.api.GitLabServerPath

@Serializable
class GitLabAccount(
  override val id: String = generateId(),
  override val name: @NlsSafe String = "",
  override val server: GitLabServerPath = GitLabServerPath()
) : ServerAccount() {
  @NlsSafe
  override fun toString(): String = "$server/$name"
}