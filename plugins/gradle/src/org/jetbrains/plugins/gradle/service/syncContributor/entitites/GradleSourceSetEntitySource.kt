// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor.entitites

/**
 * This entity source identifies all Gradle entities that are created for the [org.jetbrains.plugins.gradle.model.ExternalSourceSet].
 *
 * @see org.jetbrains.plugins.gradle.model.ExternalSourceSet
 */
class GradleSourceSetEntitySource(
  val projectEntitySource: GradleProjectEntitySource,
  val sourceSetName: String,
) : GradleEntitySource {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GradleSourceSetEntitySource) return false

    if (projectEntitySource != other.projectEntitySource) return false
    if (sourceSetName != other.sourceSetName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = projectEntitySource.hashCode()
    result = 31 * result + sourceSetName.hashCode()
    return result
  }
}