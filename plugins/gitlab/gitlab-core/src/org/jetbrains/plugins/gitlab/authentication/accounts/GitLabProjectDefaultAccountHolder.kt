// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication.accounts

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.auth.PersistentDefaultAccountHolder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
@State(name = "GitLabDefaultAccount", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)], reportStatistic = false)
internal class GitLabProjectDefaultAccountHolder(project: Project, parentCs: CoroutineScope)
  : PersistentDefaultAccountHolder<GitLabAccount>(project, parentCs.childScope(GitLabProjectDefaultAccountHolder::class)) {
  override fun accountManager() = service<GitLabAccountManager>()
  override fun notifyDefaultAccountMissing() {

  }
}