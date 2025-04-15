// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.junit5.asDynamicTests
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path

class CommunityPluginModelTest {
  @TestFactory
  fun check(): List<DynamicTest> {
    val communityPath = PlatformTestUtil.getCommunityPath()
    val options = PluginValidationOptions(
      skipUnresolvedOptionalContentModules = true,
      modulesToSkip = setOf(
        "intellij.android.device-explorer",
        "intellij.platform.syntax.psi", /* syntax.psi is not yet a real module because it's a part of Core */
      ),
      pathsIncludedFromLibrariesViaXiInclude = setOf(
        "META-INF/analysis-api/analysis-api-fe10.xml",
        "META-INF/analysis-api/analysis-api-fir.xml",
        "META-INF/wizard-template-impl.xml",
      )
    )
    val result = validatePluginModel(Path.of(communityPath), options)
    
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      val out = Path.of(communityPath, System.getProperty("plugin.graph.out", "docs/plugin-graph/plugin-graph.local.json"))
      result.writeGraph(out)
      println()
      println("Graph is written to $out")
      println("Drop file to https://plugingraph.ij.pages.jetbrains.team/ to visualize.")
    }
    
    return result.namedFailures.asDynamicTests("problems in plugin configuration")
  }
}

