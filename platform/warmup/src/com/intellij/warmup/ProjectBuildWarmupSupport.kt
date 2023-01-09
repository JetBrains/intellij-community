// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to add a custom 'Project is built/compiled' event for warmup mode.
 * All such events are awaited in warmup mode after all [ProjectIndexesWarmupSupport] were awaited.
 */
@ApiStatus.Internal
interface ProjectBuildWarmupSupport {
  companion object {
    var EP_NAME = ExtensionPointName<ProjectBuildWarmupSupport>("com.intellij.projectBuildWarmupSupport")
  }

  /**
   * Builder ID which is used to customize a set of builders (implementations of [ProjectBuildWarmupSupport])
   * which should be run. Customization can be made using environment variable IJ_WARMUP_BUILD_BUILDERS with semicolon separated builder ids
   * ```
   * IJ_WARMUP_BUILD_BUILDERS=PLATFORM;RIDER
   * ```
   * @see PlatformBuildWarmupSupport
   */
  fun getBuilderId(): String

  /**
   * Start custom build process and return a future which is completed only when a custom build is finished
   * @param rebuild indicates if rebuild should be done instead of ordinary build
   *
   * @return build status message
   */
  suspend fun buildProject(rebuild: Boolean = false): String
}