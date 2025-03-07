// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.asDynamicTests
import org.jetbrains.jps.model.module.JpsModule
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path

class PluginModelTest {
  @TestFactory
  fun check(): List<DynamicTest> {
    val communityPath = PlatformTestUtil.getCommunityPath()
    val validator = validatePluginModel(Path.of(communityPath), skipUnresolvedOptionalContentModules = true)
    
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      val out = Path.of(communityPath, System.getProperty("plugin.graph.out", "docs/plugin-graph/plugin-graph.local.json"))
      validator.writeGraph(out)
      println()
      println("Graph is written to $out")
      println("Drop file to https://plugingraph.ij.pages.jetbrains.team/ to visualize.")
    }
    
    return validator.namedFailures.asDynamicTests("problems in plugin configuration")
  }
}

fun validatePluginModel(homePath: Path, skipUnresolvedOptionalContentModules: Boolean = false): PluginModelValidator {
  val modules = IntelliJProjectConfiguration.loadIntelliJProject(homePath.toString())
    .modules
    .map { ModuleWrap(it) }

  return PluginModelValidator(modules, skipUnresolvedOptionalContentModules = skipUnresolvedOptionalContentModules)
}

private data class ModuleWrap(private val module: JpsModule) : PluginModelValidator.Module {
  override val name: String
    get() = module.name

  override val sourceRoots: Sequence<Path>
    get() {
      return module.sourceRoots
        .asSequence()
        .filter { !it.rootType.isForTests }
        .map { it.path }
    }
}