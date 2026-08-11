// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Data related to a Compose resources group.
 *
 * Corresponds to a single platform-specific subset of Compose resources (e.g., source set in Gradle or fragment in the Kotlin Toolchain).
 *
 * @see ComposeResourcesDataProvider
 */
@ApiStatus.Internal
interface ComposeResourcesData {
  /**
   * Whether the resource class is public or internal.
   */
  val isPublicResClass: Boolean

  /**
   * The name of the resource class.
   */
  val nameOfResClass: String

  /**
   * The package of the resource class.
   */
  val packageOfResClass: String

  /**
   * Path to the directory containing Compose resources.
   */
  val directoryPath: Path

  /**
   * Possible path to common resources. Can be used as a fallback when [directoryPath] doesn't exist.
   *
   * @see getComposeResourcesDir
   */
  val commonResourcesPath: Path?

  /**
   * If `true`, the resources group was defined manually as opposed to the conventional location provided by the build system.
   */
  val isCustomDirectory: Boolean

  /**
   * Path to the directory containing generated accessors.
   *
   * Note: this is an abstraction that leaks a bit.
   * It is required for the current implementation of [ComposeResourcesGenerationService] that actually updates accessors directly in the
   * file system on PSI updates (to save on calling external processes to generate accessors).
   * This could be removed if the generation is replaced with light classes (similar to how Android Res classes support works).
   */
  @get:RequiresReadLock
  val accessorsDirectory: VirtualFile?

  /**
   * A source set specific string to be used in the converted resources accessors file name.
   *
   * Note: this is an abstraction that leaks a bit.
   * See [accessorsDirectory] for details.
   */
  val accessorsQualifier: String
}