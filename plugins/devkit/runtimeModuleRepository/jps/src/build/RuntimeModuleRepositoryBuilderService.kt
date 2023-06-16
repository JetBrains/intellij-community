// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import org.jetbrains.jps.builders.BuildTargetType
import org.jetbrains.jps.incremental.BuilderService
import org.jetbrains.jps.incremental.TargetBuilder

internal class RuntimeModuleRepositoryBuilderService : BuilderService() {
  override fun getTargetTypes(): List<BuildTargetType<*>> = listOf(RuntimeModuleRepositoryTarget)
  override fun createBuilders(): List<TargetBuilder<*, *>?> = listOf(RuntimeModuleRepositoryBuilder())
}