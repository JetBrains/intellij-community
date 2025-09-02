// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.monorepo.api

import com.intellij.openapi.application.PathManager
import com.intellij.tools.apiDump.API
import com.intellij.tools.apiDump.api
import com.intellij.tools.apiDump.emptyApiIndex
import com.intellij.util.SuspendingLazy
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.fail
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.name

@ApiStatus.Internal
class ModuleApi(private val cs: CoroutineScope) {

  private val knownModules = ConcurrentHashMap<String, SuspendingLazy<API>>()

  suspend fun moduleApi(module: JpsModule): API {
    return checkNotNull(knownModules[module.name]).getValue()
  }

  fun discoverModule(module: JpsModule) {
    knownModules.computeIfAbsent(module.name) {
      cs.suspendingLazy(CoroutineName(module.name)) {
        computeModuleApi(module)
      }
    }
  }

  private suspend fun computeModuleApi(module: JpsModule): API {
    val dependencies = JpsJavaExtensionService.dependencies(module)
      .compileOnly()
      .productionOnly()
      .modules
      .filter {
        knownModules.containsKey(it.name)
      }
    val dependencyIndex = channelFlow {
      for (dependency in dependencies) {
        launch {
          val api = moduleApi(dependency)
          send(api.index)
        }
      }
    }.fold(emptyApiIndex) { acc, item ->
      acc + item
    }

    var outputDir = ProjectPaths.getModuleOutputDir(module, false)?.toPath()
                    ?: fail("'${module.name}' has no out directory")
    val mapping = PathManager.getArchivedCompiledClassesMapping()
    if (mapping != null) {
      // path is absolute, mapping contains only the last two path elements
      outputDir = mapping[outputDir.parent.name + "/" + outputDir.name]?.let { Path.of(it) } ?: outputDir
    }

    return api(dependencyIndex, outputDir)
  }
}