// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.jps.model.module.JpsModule
import org.junit.Assert
import org.junit.Test
import java.nio.file.Path

class PluginModelTest {
  @Test
  fun check() {
    val communityPath = PlatformTestUtil.getCommunityPath()
    val validator = validatePluginModel(Path.of(communityPath), skipUnresolvedOptionalContentModules = true)
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      val out = Path.of(communityPath, System.getProperty("plugin.graph.out", "docs/plugin-graph/plugin-graph.local.json"))
      validator.writeGraph(out)
      println()
      println("Graph is written to $out")
      println("Drop file to https://plugingraph.ij.pages.jetbrains.team/ to visualize.")
    }
  }
}

fun validatePluginModel(homePath: Path, skipUnresolvedOptionalContentModules: Boolean = false): PluginModelValidator {
  val modules = IntelliJProjectConfiguration.loadIntelliJProject(homePath.toString())
    .modules
    .map { ModuleWrap(it) }

  val validator = PluginModelValidator(modules, skipUnresolvedOptionalContentModules = skipUnresolvedOptionalContentModules)
  val errors = validator.errorsAsString
  if (!errors.isEmpty()) {
    System.err.println(errors)
    Assert.fail(errors.toString().take(1000))
  }
  return validator
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