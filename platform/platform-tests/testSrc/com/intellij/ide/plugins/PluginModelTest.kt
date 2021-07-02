// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil.urlToPath
import org.junit.Assert
import org.junit.Test
import java.nio.file.Path

class PluginModelTest {
  @Test
  fun check() {
    val modules = IntelliJProjectConfiguration.loadIntelliJProject(homePath.toString())
      .modules
      .map { wrap(it) }

    val validator = PluginModelValidator(modules)
    val errors = validator.errorsAsString
    if (!errors.isEmpty()) {
      System.err.println(errors)
      Assert.fail()
    }

    if (!IS_UNDER_TEAMCITY) {
      val out = Path.of(getCommunityPath(), System.getProperty("plugin.graph.out", "docs/plugin-graph/plugin-graph.local.json"))
      validator.writeGraph(out)
      println()
      println("Graph is written to $out")
      println("Drop file to https://plugingraph.ij.pages.jetbrains.team/ to visualize.")
    }
  }

  private fun wrap(module: JpsModule) = object : PluginModelValidator.Module {
    override val name: String
      get() = module.name

    override val sourceRoots: List<Path>
      get() {
        return module.sourceRoots
          .asSequence()
          .filter { !it.rootType.isForTests }
          .map { it.url }
          .map(::urlToPath)
          .map(Path::of)
          .toList()
      }
  }
}