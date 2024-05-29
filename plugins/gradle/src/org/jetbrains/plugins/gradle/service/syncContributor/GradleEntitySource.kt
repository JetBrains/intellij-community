// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * Any Gradle Entity for Workspace Model inherits this marker interface
 */
interface GradleEntitySource : EntitySource

/**
 * This entity source identifies all Gradle entities that are created for the [org.jetbrains.plugins.gradle.model.GradleLightBuild].
 *
 * @see org.gradle.tooling.model.BuildIdentifier
 * @see org.jetbrains.plugins.gradle.model.GradleLightBuild
 */
class GradleBuildEntitySource(
  buildUrl: VirtualFileUrl
) : GradleEntitySource {

  override val virtualFileUrl: VirtualFileUrl = buildUrl

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleBuildEntitySource) return false

    if (virtualFileUrl != other.virtualFileUrl) return false

    return true
  }

  override fun hashCode(): Int {
    return virtualFileUrl.hashCode()
  }
}