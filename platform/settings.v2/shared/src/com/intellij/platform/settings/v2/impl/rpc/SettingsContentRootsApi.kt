// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.rpc

// @spec ../../../../../../../../../spec/frontend-file-contexts.spec.md

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Holds the content roots of one project, as the side that owns them sees them.
 *
 * @property rootUrls the topmost content root urls, sorted. No root lies under another. See
 *   [com.intellij.platform.settings.v2.impl.collapseNestedRoots].
 */
@Internal
@Serializable
data class ProjectContentRoots(val rootUrls: List<String>)

/**
 * Carries a project's content roots from the backend to a frontend, which needs them to build a file context. The
 * roots say which root holds a file, and whether the file is under a root at all.
 */
@Internal
@Rpc
interface SettingsContentRootsApi : RemoteApi<Unit> {
  /**
   * Returns the content roots of the [projectId] project, then every later state of them. The first value is the
   * state at the moment of the call, so a caller needs no separate request for it.
   */
  suspend fun contentRoots(projectId: ProjectId): Flow<ProjectContentRoots>

  companion object {
    suspend fun getInstance(): SettingsContentRootsApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<SettingsContentRootsApi>())
  }
}
