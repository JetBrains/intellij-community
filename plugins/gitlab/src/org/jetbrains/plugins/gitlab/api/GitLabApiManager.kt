// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.StateFlow

@Service
class GitLabApiManager {
  fun getClient(token: String): GitLabApi = GitLabApiImpl { token }
}