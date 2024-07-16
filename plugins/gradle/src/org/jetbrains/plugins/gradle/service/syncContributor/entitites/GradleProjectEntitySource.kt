// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.entitites

import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * This entity source identifies all Gradle entities that are created for the [org.jetbrains.plugins.gradle.model.GradleLightProject].
 *
 * @see org.gradle.tooling.model.ProjectIdentifier
 * @see org.jetbrains.plugins.gradle.model.GradleLightProject
 */
class GradleProjectEntitySource(
  val buildEntitySource: GradleBuildEntitySource,
  val projectRootUrl: VirtualFileUrl,
) : GradleEntitySource {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleProjectEntitySource) return false

    if (buildEntitySource != other.buildEntitySource) return false
    if (projectRootUrl != other.projectRootUrl) return false

    return true
  }

  override fun hashCode(): Int {
    var result = buildEntitySource.hashCode()
    result = 31 * result + projectRootUrl.hashCode()
    return result
  }
}