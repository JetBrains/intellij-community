// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.entitites

import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * This entity source identifies project entities created by GradleDeclarativeSyncContributor
 */
class GradleDeclarativeEntitySource(val projectRootUrl: VirtualFileUrl) : GradleEntitySource {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleDeclarativeEntitySource) return false

    if (projectRootUrl != other.projectRootUrl) return false

    return true
  }

  override fun hashCode(): Int {
    return projectRootUrl.hashCode()
  }
}
