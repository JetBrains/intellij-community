// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.DisabledPluginsState.Companion.saveDisabledPluginsAndInvalidate
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.computeEssentialPlugins
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.configureProductModeModules
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.TriConsumer
import com.intellij.util.system.CpuArch
import com.intellij.util.system.OS
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

class PluginManagerTest {
  @Rule
  @JvmField
  val tempDir: TempDirectory = TempDirectory()

  @Test
  fun compatibilityBranchBased() {
    assertCompatible("145.2", null, null)
    assertCompatible("145.2.2", null, null)

    assertCompatible("145.2", "145", null)
    assertCompatible("145.2", null, "146")
    assertCompatible("145.2.2", "145", null)
    assertCompatible("145.2.2", null, "146")
    assertIncompatible("145.2", null, "145")

    assertIncompatible("145.2", "146", null)
    assertIncompatible("145.2", null, "144")
    assertIncompatible("145.2.2", "146", null)
    assertIncompatible("145.2.2", null, "144")

    assertCompatible("145.2", "145.2", null)
    assertCompatible("145.2", null, "145.2")
    assertCompatible("145.2.2", "145.2", null)
    assertIncompatible("145.2.2", null, "145.2")

    assertIncompatible("145.2", "145.3", null)
    assertIncompatible("145.2", null, "145.1")
    assertIncompatible("145.2.2", "145.3", null)
    assertIncompatible("145.2.2", null, "145.1")

    assertCompatible("145.2", "140.3", null)
    assertCompatible("145.2", null, "146.1")
    assertCompatible("145.2.2", "140.3", null)
    assertCompatible("145.2.2", null, "146.1")

    assertIncompatible("145.2", "145.2.0", null)
    assertIncompatible("145.2", "145.2.1", null)
    assertCompatible("145.2", null, "145.2.3")
    assertCompatible("145.2.2", "145.2.0", null)
    assertCompatible("145.2.2", null, "145.2.3")
  }

  @Test
  fun ignoredCompatibility() {
    val checkCompatibility = TriConsumer { ideVersion: String?, sinceBuild: String?, untilBuild: String? ->
      val ignoreCompatibility = PluginManagerCore.isIgnoreCompatibility
      try {
        assertIncompatible(ideVersion, sinceBuild, untilBuild)

        PluginManagerCore.isIgnoreCompatibility = true
        assertCompatible(ideVersion, sinceBuild, untilBuild)
      }
      finally {
        PluginManagerCore.isIgnoreCompatibility = ignoreCompatibility
      }
    }

    checkCompatibility.accept("42", "43", null)
    checkCompatibility.accept("43", null, "42")
  }

  @Test
  fun compatibilityBranchBasedStar() {
    assertCompatible("145.10", "144.*", null)
    assertIncompatible("145.10", "145.*", null)
    assertIncompatible("145.10", "146.*", null)
    assertIncompatible("145.10", null, "144.*")
    assertCompatible("145.10", null, "145.*")
    assertCompatible("145.10", null, "146.*")

    assertCompatible("145.10.1", null, "145.*")
    assertCompatible("145.10.1", "145.10", "145.10.*")

    assertCompatible("145.SNAPSHOT", null, "145.*")
  }

  @Test
  fun compatibilitySnapshots() {
    assertIncompatible("145.SNAPSHOT", "146", null)
    assertIncompatible("145.2.SNAPSHOT", "145.3", null)

    assertCompatible("145.SNAPSHOT", "145.2", null)

    assertCompatible("145.SNAPSHOT", null, "146")
    assertIncompatible("145.SNAPSHOT", null, "145")
    assertIncompatible("145.SNAPSHOT", null, "144")
    assertIncompatible("145.2.SNAPSHOT", null, "145")
    assertIncompatible("145.2.SNAPSHOT", null, "144")
  }

  @Test
  fun compatibilityPlatform() {
    assertEquals(OS.CURRENT == OS.Windows, checkCompatibility("com.intellij.modules.os.windows"))
    assertEquals(OS.CURRENT == OS.macOS, checkCompatibility("com.intellij.modules.os.mac"))
    assertEquals(OS.CURRENT == OS.Linux, checkCompatibility("com.intellij.modules.os.linux"))
    assertEquals(OS.CURRENT == OS.FreeBSD, checkCompatibility("com.intellij.modules.os.freebsd"))
    assertEquals(OS.CURRENT != OS.Windows, checkCompatibility("com.intellij.modules.os.unix"))
    assertEquals(OS.isGenericUnix(), checkCompatibility("com.intellij.modules.os.xwindow"))
  }

  @Test
  fun compatibilityCpu() {
    assertEquals(CpuArch.CURRENT == CpuArch.X86, checkCompatibility("com.intellij.modules.arch.x86"))
    assertEquals(CpuArch.CURRENT == CpuArch.X86_64, checkCompatibility("com.intellij.modules.arch.x86_64"))
    assertEquals(CpuArch.CURRENT == CpuArch.ARM32, checkCompatibility("com.intellij.modules.arch.arm32"))
    assertEquals(CpuArch.CURRENT == CpuArch.ARM64, checkCompatibility("com.intellij.modules.arch.arm64"))
  }

  @Test
  fun convertExplicitBigNumberInUntilBuildToStar() {
    assertConvertsTo(null, null)
    assertConvertsTo("145", "145")
    assertConvertsTo("145.999", "145.999")
    assertConvertsTo("145.9999", "145.*")
    assertConvertsTo("145.99999", "145.*")
    assertConvertsTo("145.9999.1", "145.9999.1")
    assertConvertsTo("145.1000", "145.1000")
    assertConvertsTo("145.10000", "145.*")
    assertConvertsTo("145.100000", "145.*")
  }

  @Test
  @Throws(IOException::class)
  fun testSymlinkInConfigPath() {
    IoTestUtil.assumeSymLinkCreationIsSupported()

    val configPath = tempDir.root.toPath().resolve("config-link")
    val target = tempDir.newDirectory("config-target").toPath()
    Files.createSymbolicLink(configPath, target)
    saveDisabledPluginsAndInvalidate(configPath, mutableListOf("a"))
    com.intellij.testFramework.assertions.Assertions.assertThat(configPath.resolve(
      DisabledPluginsState.DISABLED_PLUGINS_FILENAME)).hasContent("a" + System.lineSeparator())
  }

  @Test
  fun `product mode modules match the gold data`() {
    val modes = listOf(
      ProductMode.MONOLITH to listOf(
        "+ intellij.platform.backend",
        "- intellij.platform.backend.split",
        "+ intellij.platform.frontend",
        "- intellij.platform.frontend.split",
        "- intellij.platform.frontend.split.base",
        "+ intellij.platform.jps.build.dependencyGraph",
        "- intellij.platform.split",
      ),
      ProductMode.BACKEND to listOf(
        "+ intellij.platform.backend",
        "+ intellij.platform.backend.split",
        "- intellij.platform.frontend",
        "- intellij.platform.frontend.split",
        "- intellij.platform.frontend.split.base",
        "+ intellij.platform.jps.build.dependencyGraph",
        "+ intellij.platform.split",
      ),
      ProductMode.FRONTEND to listOf(
        "- intellij.platform.backend",
        "- intellij.platform.backend.split",
        "+ intellij.platform.frontend",
        "+ intellij.platform.frontend.split",
        "+ intellij.platform.frontend.split.base",
        "- intellij.platform.jps.build.dependencyGraph",
        "+ intellij.platform.split",
      ),
      ProductMode.LIGHT to listOf(
        "- intellij.cwm.plugin.common",
        "- intellij.platform.backend",
        "- intellij.platform.backend.split",
        "+ intellij.platform.frontend",
        "- intellij.platform.frontend.split",
        "+ intellij.platform.frontend.split.base",
        "- intellij.platform.jps.build.dependencyGraph",
        "- intellij.platform.rpc",
        "- intellij.platform.split",
        "- intellij.platform.split.connection",
        "- intellij.rd.client",
      ),
      ProductMode.LIGHT_WITH_RD_CONNECTION to listOf(
        "- intellij.cwm.plugin.common",
        "- intellij.platform.backend",
        "- intellij.platform.backend.split",
        "+ intellij.platform.frontend",
        "- intellij.platform.frontend.split",
        "+ intellij.platform.frontend.split.base",
        "- intellij.platform.jps.build.dependencyGraph",
        "- intellij.platform.rpc",
        "- intellij.platform.split",
        "+ intellij.platform.split.connection",
        "- intellij.rd.client",
      ),
      ProductMode.LANGUAGE_SERVER to listOf(
        "+ intellij.platform.backend",
        "- intellij.platform.backend.split",
        "- intellij.platform.frontend",
        "- intellij.platform.frontend.split",
        "- intellij.platform.frontend.split.base",
      ),
    )
    for ((currentMode, expectedValues) in modes) {
      val map = buildMap {
        configureProductModeModules(currentMode.id)
      }
      val actual = map.map { it.key.name to it.value.isAvailable }.sortedBy { it.first }
        .joinToString("\n") { (if (it.second) "+ " else "- ") + it.first }
      val expected = expectedValues.joinToString("\n")
      assertEquals("Product modules for '${currentMode.id}' do not match gold data", expected, actual)
    }
  }

  @Test
  fun `essential plugins are derived from the product mode`() {
    val remoteDev = PluginId.getId("com.jetbrains.remoteDevelopment")
    val declared = listOf(PluginId.getId("com.intellij.java"))
    val modesWithRemoteDev = setOf(ProductMode.BACKEND, ProductMode.FRONTEND, ProductMode.LIGHT, ProductMode.LIGHT_WITH_RD_CONNECTION)
    listOf(ProductMode.MONOLITH, ProductMode.FRONTEND, ProductMode.BACKEND,
           ProductMode.LIGHT, ProductMode.LIGHT_WITH_RD_CONNECTION, ProductMode.LANGUAGE_SERVER).forEach { mode ->
      val expected = listOf(PluginManagerCore.CORE_ID, declared.single()) + listOfNotNull(remoteDev.takeIf { mode in modesWithRemoteDev })
      assertThat(computeEssentialPlugins(declared, productModeId = mode.id))
        .describedAs("essential plugins for '${mode.id}'")
        .containsExactlyInAnyOrderElementsOf(expected)
    }
  }

  // TODO probably should be moved elsewhere
  @Test
  fun `unfulfilled os requirement triggers only on required dependencies`() {
    data class PluginDependency(override val pluginId: PluginId, override val isOptional: Boolean) : IdeaPluginDependency
    for (module in IdeaPluginOsRequirement.entries) {
      val required = object : TestIdeaPluginDescriptor() {
        override fun getDependencies(): List<IdeaPluginDependency> = listOf(PluginDependency(module.moduleId, false))
      }
      val optional = object : TestIdeaPluginDescriptor() {
        override fun getDependencies(): List<IdeaPluginDependency> = listOf(PluginDependency(module.moduleId, true))
      }
      assertThat(PluginCompatibilityUtils.getUnfulfilledOsRequirement(required)).isEqualTo(module.takeIf { !module.isHostOs() })
      assertThat(PluginCompatibilityUtils.getUnfulfilledOsRequirement(optional)).isEqualTo(null)
    }
  }

  @Test
  fun `unfulfilled os requirement is inferred from version when dependencies are empty`() {
    fun descriptor(version: String?) = object : TestIdeaPluginDescriptor() {
      override fun getDependencies(): List<IdeaPluginDependency> = emptyList()
      override fun getVersion(): String? = version
      override fun getPluginId(): PluginId = PluginId.getId("test.plugin")
    }
    fun assertInferred(version: String?, expected: IdeaPluginOsRequirement?) {
      assertThat(PluginCompatibilityUtils.getUnfulfilledOsRequirement(descriptor(version)))
        .isEqualTo(expected?.takeIf { !it.isHostOs() })
    }

    assertInferred("1.0.0-windows-amd64", IdeaPluginOsRequirement.Windows)
    assertInferred("1.0.0-mac-arm64", IdeaPluginOsRequirement.Mac)
    assertInferred("1.0.0-linux-amd64", IdeaPluginOsRequirement.Linux)
    assertInferred("1.0.0-freebsd-amd64", IdeaPluginOsRequirement.FreeBSD)
    // unrecognized OS tag maps to OS.Other and is filtered out
    assertInferred("1.0.0-solaris-amd64", null)
    // versions that do not match the <version>-<os>-<arch> pattern infer nothing
    assertInferred("1.0.0", null)
    assertInferred("241.SNAPSHOT", null)
    // a missing version must not throw and infers nothing
    assertInferred(null, null)
  }

  @Test
  fun `unfulfilled cpu arch requirement is inferred from version when dependencies are empty`() {
    fun descriptor(version: String?) = object : TestIdeaPluginDescriptor() {
      override fun getDependencies(): List<IdeaPluginDependency> = emptyList()
      override fun getVersion(): String? = version
      override fun getPluginId(): PluginId = PluginId.getId("test.plugin")
    }
    fun assertInferred(version: String?, expected: PluginCpuArchRequirement?) {
      assertThat(PluginCompatibilityUtils.getUnfulfilledCpuArchRequirement(descriptor(version)))
        .isEqualTo(expected?.takeIf { !it.isHostArch() })
    }

    assertInferred("1.0.0-windows-amd64", PluginCpuArchRequirement.X86_64)
    assertInferred("1.0.0-windows-x86_64", PluginCpuArchRequirement.X86_64)
    assertInferred("1.0.0-windows-x86", PluginCpuArchRequirement.X86)
    assertInferred("1.0.0-windows-arm64", PluginCpuArchRequirement.ARM64)
    assertInferred("1.0.0-windows-aarch64", PluginCpuArchRequirement.ARM64)
    // unrecognized arch tag maps to CpuArch.OTHER/UNKNOWN and is filtered out
    assertInferred("1.0.0-windows-sparc", null)
    // versions that do not match the <version>-<os>-<arch> pattern infer nothing
    assertInferred("1.0.0", null)
    // a missing version must not throw and infers nothing
    assertInferred(null, null)
  }

  companion object {
    private fun assertConvertsTo(untilBuild: String?, result: String?) {
      assertEquals(result, PluginManager.convertExplicitBigNumberInUntilBuildToStar(untilBuild))
    }

    private fun assertIncompatible(ideVersion: String?, sinceBuild: String?, untilBuild: String?) {
      Assert.assertNotNull(checkCompatibility(ideVersion, sinceBuild, untilBuild))
    }

    private fun checkCompatibility(ideVersion: String?, sinceBuild: String?, untilBuild: String?): PluginIncompatibilityReason? {
      val desc = object : TestIdeaPluginDescriptor() {
        override fun getPluginId(): PluginId = PluginId.getId("test")
        override fun getName(): @NlsSafe String? = pluginId.idString
        override fun getSinceBuild(): @NlsSafe String? = sinceBuild
        override fun getUntilBuild(): @NlsSafe String? = untilBuild
        override fun getVersion(): @NlsSafe String? = null
        override fun getDependencies(): List<IdeaPluginDependency> = listOf()
      }
      return PluginCompatibilityUtils.checkBuildNumberCompatibility(desc, BuildNumber.fromString(ideVersion)!!)
    }

    private fun checkCompatibility(platformId: String): Boolean {
      val desc = object : TestIdeaPluginDescriptor() {
        override fun getPluginId(): PluginId = PluginId.getId("test")
        override fun getName(): @NlsSafe String? = pluginId.idString
        override fun getSinceBuild(): @NlsSafe String? = null
        override fun getUntilBuild(): @NlsSafe String? = null
        override fun getVersion(): @NlsSafe String? = null
        override fun getDependencies(): List<IdeaPluginDependency> = listOf(
          object : IdeaPluginDependency {
            override val pluginId: PluginId = PluginId.getId(platformId)
            override val isOptional: Boolean = false
          }
        )
      }
      return PluginCompatibilityUtils.checkBuildNumberCompatibility(desc, BuildNumber.fromString("145")!!) == null
    }

    private fun assertCompatible(ideVersion: String?, sinceBuild: String?, untilBuild: String?) {
      Assert.assertNull(checkCompatibility(ideVersion, sinceBuild, untilBuild))
    }
  }
}
