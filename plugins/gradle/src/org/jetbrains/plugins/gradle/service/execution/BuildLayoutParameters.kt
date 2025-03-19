// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.execution.target.value.MapTargetValue
import com.intellij.execution.target.value.TargetValue
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * [BuildLayoutParameters] describes layout of the target Gradle build which is supposed to be used according to IDE configuration of the linked Gradle project
 * and [com.intellij.execution.target.TargetEnvironment] provided by [com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider].
 *
 * Implementations are expected to be target build environment specific.
 *
 * @see org.jetbrains.plugins.gradle.service.GradleInstallationManager.guessBuildLayoutParameters
 * @see org.gradle.initialization.BuildLayoutParameters
 * @see org.jetbrains.plugins.gradle.settings.GradleProjectSettings
 * @see org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
 */
interface BuildLayoutParameters {
  /**
   * Gradle installation directory resolved based on the execution parameters of the build.
   */
  val gradleHome: TargetValue<Path>?

  /**
   * https://docs.gradle.org/current/userguide/directory_layout.html#dir:gradle_user_home
   */
  val gradleUserHomePath: TargetValue<Path>

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use BuildLayoutParameters#gradleUserHomePath")
  fun getGradleUserHome(): TargetValue<String> {
    return MapTargetValue(gradleUserHomePath) {
      it.toString()
    }
  }

  /**
   * Gradle version which likely be used for the target build, 'null' means it can not resolved or even guessed.
   */
  val gradleVersion: GradleVersion?
}