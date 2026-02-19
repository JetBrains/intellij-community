// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.GHRepositoryConnection

@ApiStatus.Internal
interface GHPRConnectedProjectViewModelFactory {
  fun create(project: Project, cs: CoroutineScope, connection: GHRepositoryConnection, activateProject: () -> Unit): GHPRConnectedProjectViewModel
}
