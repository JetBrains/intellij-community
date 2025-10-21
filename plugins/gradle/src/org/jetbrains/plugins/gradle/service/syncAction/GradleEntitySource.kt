// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction

import com.intellij.platform.workspace.storage.EntitySource
import org.jetbrains.annotations.ApiStatus

/**
 * The Gradle workspace entity source, for all Gradle entities
 * that created during Gradle sync.
 */
@ApiStatus.Experimental
interface GradleEntitySource : EntitySource {

  /**
   * Identifies entities in the one independent Gradle model.
   *
   * Usually correspond to the linked Gradle project [org.jetbrains.plugins.gradle.settings.GradleProjectSettings].
   * However, for Gradle older than 8.0, the buildSrc is synced as independent Gradle model.
   * Therefore, it needs separately identified entity source to avoid the main Gradle model entities' replacement.
   *
   * @see org.jetbrains.plugins.gradle.service.project.ProjectResolverContext.projectPath
   */
  val projectPath: String

  /**
   * Identifies entities in the one Gradle sync phase.
   *
   * It allows replacing only updated entities during Gradle phased sync.
   * And allows keeping entities for late phases from the previous Gradle sync.
   */
  val phase: GradleSyncPhase

  // Expected to be implemented as private data class
  override fun equals(other: Any?): Boolean
  override fun hashCode(): Int
  override fun toString(): String
}