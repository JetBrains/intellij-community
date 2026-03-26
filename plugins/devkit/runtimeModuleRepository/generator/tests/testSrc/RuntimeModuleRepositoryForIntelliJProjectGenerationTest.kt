// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.generator.tests

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.model.serialization.JpsSerializationManager
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.pathString

class RuntimeModuleRepositoryForIntelliJProjectGenerationTest {
  @Test
  fun `test community project`() {
    val project = JpsSerializationManager.getInstance().loadProject(PathManager.getCommunityHomePath(), emptyMap<String, String>())
    generateAndValidateRuntimeModuleRepository(project)
  }

  @Test
  fun `test ultimate project`() {
    val ultimateRoot = Path(PathManager.getCommunityHomePath()).parent
    if (!Files.exists(ultimateRoot.resolve(".idea"))) return
    val project = JpsSerializationManager.getInstance().loadProject(ultimateRoot.pathString, emptyMap<String, String>())
    generateAndValidateRuntimeModuleRepository(project)
  }
}