// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.runtimeModuleRepository.jps.build

import com.intellij.openapi.application.PathManager
import org.jetbrains.jps.builders.CompileScopeTestBuilder
import org.jetbrains.jps.builders.JpsBuildTestCase
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.pathString

class BuildModuleRepositoryForIntelliJProjectTest : JpsBuildTestCase() {
  fun `test community project`() {
    loadProject(PathManager.getCommunityHomePath())
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = getUrl("out")
    doBuild(CompileScopeTestBuilder.make().targetTypes(RuntimeModuleRepositoryTarget)).assertSuccessful()
  }
  
  fun `test ultimate project`() {
    val ultimateRoot = Path(PathManager.getCommunityHomePath()).parent
    if (!Files.exists(ultimateRoot.resolve(".idea"))) return
    loadProject(ultimateRoot.pathString)
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).outputUrl = getUrl("out")
    doBuild(CompileScopeTestBuilder.make().targetTypes(RuntimeModuleRepositoryTarget)).assertSuccessful()
  }
}