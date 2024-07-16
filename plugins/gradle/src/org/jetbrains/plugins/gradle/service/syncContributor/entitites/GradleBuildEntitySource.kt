// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.entitites

import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * This entity source identifies all Gradle entities that are created for the [org.jetbrains.plugins.gradle.model.GradleLightBuild].
 *
 * @see org.gradle.tooling.model.BuildIdentifier
 * @see org.jetbrains.plugins.gradle.model.GradleLightBuild
 */
class GradleBuildEntitySource(
  val linkedProjectEntitySource: GradleLinkedProjectEntitySource,
  val buildRootUrl: VirtualFileUrl,
) : GradleEntitySource {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleBuildEntitySource) return false

    if (linkedProjectEntitySource != other.linkedProjectEntitySource) return false
    if (buildRootUrl != other.buildRootUrl) return false

    return true
  }

  override fun hashCode(): Int {
    return buildRootUrl.hashCode()
  }
}