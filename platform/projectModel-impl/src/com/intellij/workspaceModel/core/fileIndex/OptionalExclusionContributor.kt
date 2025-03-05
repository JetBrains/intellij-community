// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface OptionalExclusionContributor {
  /**
   * @return true if the url has been excluded
   */
  fun exclude(project: Project, url: VirtualFileUrl): Boolean

  /**
   * @return true if exclusion of the url has been canceled
   */
  fun cancelExclusion(project: Project, url: VirtualFileUrl): Boolean
}
