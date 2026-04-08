// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.project.loadIntelliJProject
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.asDynamicTests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path

private val communityPluginModelBuilderOptions = SourceCodeBasedPluginModelBuilderOptions(
  modulesWithIncorrectlyPlacedModuleDescriptor = setOf(
    "intellij.android.device-explorer",
  ),
  prefixesOfPathsIncludedFromLibrariesViaXiInclude = listOf(
    "META-INF/analysis-api/analysis-api-fe10.xml",
    "META-INF/analysis-api/analysis-api-fir.xml",
    "META-INF/wizard-template-impl.xml",
    "META-INF/tips-",
  ),
  additionalPatternsOfDirectoriesContainingIncludedXmlFiles = listOf(
    "org/jetbrains/android/dom",
    "com/android/tools/idea/ui/resourcemanager/META-INF",
  ),
  pluginVariantsWithDynamicIncludes = listOf(
    PluginVariantWithDynamicIncludes(
      mainModuleName = "kotlin.plugin",
      systemPropertyName = "idea.kotlin.plugin.use.k1",
      systemPropertyValue = "false",
    ),
    PluginVariantWithDynamicIncludes(
      mainModuleName = "kotlin.plugin",
      systemPropertyName = "idea.kotlin.plugin.use.k1",
      systemPropertyValue = "true",
    ),
  ),
)

val communityPluginValidationOptions: PluginValidationOptions = PluginValidationOptions(
  pluginModelBuilderOptions = communityPluginModelBuilderOptions,
  skipUnresolvedOptionalContentModules = true,
  // There are a number of platform services that are overridden in ultimate only. Instead of declaring all of them here, we
  // only perform the check once in AllProductsPackagingTest.pluginModel.
  skipServicesOverridesCheck = true,
  filesNamedLikeContentModuleDescriptorsButIncludedViaXiInclude = setOf(
    "intellij.platform.project.xml",
    "intellij.platform.ide.progress.xml",
    "intellij.platform.experiment.xml",
    "intellij.platform.feedback.xml",
    "intellij.platform.bookmarks.xml",
    "intellij.platform.syntax.psi.xml",
    "intellij.platform.remoteServers.impl.xml",
    "intellij.vcs.git.xml",
    "kotlin.plugin.k2.xml",
    "kotlin.plugin.k1.xml",
  ),
  referencedPluginIdsOfExternalPlugins = setOf(
    // These modules are defined in the Ultimate part.
    "com.intellij.marketplace",
    "com.intellij.modules.python-in-mini-ide-capable",
    "com.intellij.modules.rider",
    "com.intellij.modules.ultimate",
    "com.intellij.jetbrains.client",
    "com.intellij.modules.appcode.ide",
  ),
  componentImplementationClassesToIgnore = setOf(
    "com.intellij.designer.DesignerToolWindowManager",
    "com.intellij.designer.palette.PaletteToolWindowManager",
  ),
)

class CommunityPluginModelTest {
  @TestFactory
  fun check(): List<DynamicTest> {
    assumeTrue(System.getenv("TEAMCITY_VERSION") == null, "Covered by AllProductsPackagingTest on TeamCity")

    val communityPath = Path.of(PlatformTestUtil.getCommunityPath())
    val result = runBlocking(Dispatchers.Default) {
      validatePluginModel(loadIntelliJProject(communityPath), communityPath, communityPluginValidationOptions)
    }
    return result.getNamedFailures().toList().asDynamicTests("problems in plugin configuration")
  }
}
