// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

object RuntimeModuleRepositoryBuildConstants {
  const val JAR_REPOSITORY_FILE_NAME: String = "module-descriptors.jar"
  const val GENERATOR_VERSION: Int = 1

  /**
   * Must be equal to the [org.jetbrains.idea.devkit.build.IntelliJModuleRepositoryBuildScopeProvider.TARGET_TYPE_ID]
   */
  const val TARGET_TYPE_ID: String = "intellij-runtime-module-repository"
}
