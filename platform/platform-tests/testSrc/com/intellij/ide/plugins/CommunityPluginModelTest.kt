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
    val communityPath = Path.of(PlatformTestUtil.getCommunityPath())
    val options = PluginValidationOptions(
      skipUnresolvedOptionalContentModules = true,
      referencedPluginIdsOfExternalPlugins = setOf(
        //these modules are defined in the ultimate part
        "com.intellij.marketplace",
        "com.intellij.modules.python-in-mini-ide-capable",
        "com.intellij.modules.rider",
        "com.intellij.modules.ultimate",
        "com.intellij.jetbrains.client",
        "com.intellij.modules.appcode.ide",
      ),
      modulesWithIncorrectlyPlacedModuleDescriptor = setOf(
        "intellij.android.device-explorer",
      ),
      prefixesOfPathsIncludedFromLibrariesViaXiInclude = listOf(
        "META-INF/analysis-api/analysis-api-fe10.xml",
        "META-INF/analysis-api/analysis-api-fir.xml",
        "META-INF/wizard-template-impl.xml",
        "META-INF/tips-"
      ),
      additionalPatternsOfDirectoriesContainingIncludedXmlFiles = listOf(
        "org/jetbrains/android/dom",
        "com/android/tools/idea/ui/resourcemanager/META-INF",
      ),
      componentImplementationClassesToIgnore = setOf(
        "com.intellij.designer.DesignerToolWindowManager",
        "com.intellij.designer.palette.PaletteToolWindowManager",
      ),
      pluginVariantsWithDynamicIncludes = listOf(
        PluginVariantWithDynamicIncludes(
          mainModuleName = "kotlin.plugin",
          systemPropertyName = "idea.kotlin.plugin.use.k2",
          systemPropertyValue = "true",
        )
      )
    )
    val result = validatePluginModel(communityPath, options)
    
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      val out = communityPath.resolve(System.getProperty("plugin.graph.out", "docs/plugin-graph/plugin-graph.local.json"))
      result.writeGraph(out, communityPath)
      println()
      println("Graph is written to $out")
      println("Drop file to https://plugingraph.ij.pages.jetbrains.team/ to visualize.")
    }
    
    return result.namedFailures.asDynamicTests("problems in plugin configuration")
  }
}

