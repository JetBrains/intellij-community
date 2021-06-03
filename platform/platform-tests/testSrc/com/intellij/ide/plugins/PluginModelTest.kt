// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins

import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.getErrorsAsString
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.Assert
import org.junit.Test
import java.nio.file.Path

class PluginModelTest {
  @Test
  fun check() {
    val modules = IntelliJProjectConfiguration.loadIntelliJProject(PluginModelValidator.homePath).modules.map { module ->
      object : PluginModelValidator.Module {
        override val name: String
          get() = module.name

        override fun getSourceRoots(): List<Path> {
          return module.sourceRoots.asSequence()
            .filter { !it.rootType.isForTests }
            .map { Path.of(JpsPathUtil.urlToPath(it.url)) }
            .toList()
        }
      }
    }
    val validator = PluginModelValidator()
    val errors = validator.validate(modules)
    if (!errors.isEmpty()) {
      System.err.println(getErrorsAsString(errors, includeStackTrace = false))
      Assert.fail()
    }

    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      val out = Path.of(PlatformTestUtil.getCommunityPath(), "docs/plugin-graph/plugin-graph.local.json")
      validator.writeGraph(out)
      println("\nGraph is written to $out")
      println("Drop file to https://ij-platform-flow.develar.org/plugin-graph to visualize.")
    }
  }
}