// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.system.CpuArch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class PluginLoadingProblemTest {
  @TempDir
  lateinit var tempDir: Path

  @TestFactory
  fun `loading problem factories preserve their presentation`(): List<DynamicTest> {
    plugin("affected") {
      name = "Affected"
      version = "1.0"
      sinceBuild = "241"
      strictUntilBuild = "251.*"
      packagePrefix = "affected.prefix"
    }.installAt(tempDir)
    plugin("dependency") {
      name = "Dependency"
      packagePrefix = "dependency.prefix"
    }.installAt(tempDir)
    val descriptors = PluginSetTestBuilder.fromPath(tempDir).discoverPlugins().second.pluginLists.single().plugins
    val affected = descriptors.single { it.pluginId.idString == "affected" }
    val dependency = descriptors.single { it.pluginId.idString == "dependency" }
    val dependencyId = dependency.pluginId
    val buildNumber = BuildNumber.fromString("IU-263.1")!!

    fun message(key: String, vararg args: Any?): String = CoreBundle.message("plugin.loading.error.$key", *args)

    fun case(
      name: String,
      problem: PluginLoadingProblem,
      shortMessage: String,
      detailedMessage: String,
      shouldNotifyUser: Boolean = true,
      disabledDependencyId: PluginId? = null,
    ): DynamicTest = DynamicTest.dynamicTest(name) {
      assertThat(problem.plugin).isSameAs(affected)
      assertThat(problem.shortMessage).isEqualTo(shortMessage)
      assertThat(problem.detailedMessage).isEqualTo(detailedMessage)
      assertThat(problem.shouldNotifyUser).isEqualTo(shouldNotifyUser)
      assertThat(problem.disabledDependencyId).isEqualTo(disabledDependencyId)
      assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
      assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    }

    return listOf(
      case("disabled dependency", pluginDependencyIsDisabled(affected, dependencyId, true),
           message("short.depends.on.disabled.plugin", dependencyId),
           message("long.depends.on.disabled.plugin", "Affected", dependencyId), disabledDependencyId = dependencyId),
      case("hidden disabled dependency", pluginDependencyIsDisabled(affected, dependencyId, false),
           message("short.depends.on.disabled.plugin", dependencyId),
           message("long.depends.on.disabled.plugin", "Affected", dependencyId), false, dependencyId),
      case("custom loading disabled", nonBundledPluginsAreExplicitlyDisabled(affected),
           message("short.custom.plugin.loading.disabled"),
           message("long.custom.plugin.loading.disabled", "Affected"), false),
      case("broken version", pluginIsMarkedBroken(affected),
           message("short.marked.as.broken"), message("long.marked.as.broken", "Affected", "1.0")),
      case("IDEA only", pluginIsCompatibleOnlyWithIntelliJIDEA(affected),
           message("short.compatible.with.intellij.idea.only"), message("long.compatible.with.intellij.idea.only", "Affected")),
      case("host platform", pluginIsIncompatibleWithHostPlatform(affected, IdeaPluginOsRequirement.Windows, "Linux"),
           message("short.incompatible.with.platform", IdeaPluginOsRequirement.Windows),
           message("long.incompatible.with.platform", "Affected", "1.0", IdeaPluginOsRequirement.Windows, "Linux")),
      case("host CPU", pluginIsIncompatibleWithHostCpu(affected, PluginCpuArchRequirement.X86_64, CpuArch.ARM64),
           message("short.incompatible.with.platform", PluginCpuArchRequirement.X86_64),
           message("long.incompatible.with.cpu", "Affected", "1.0", PluginCpuArchRequirement.X86_64, CpuArch.ARM64)),
      case("since build", pluginSinceBuildConstraintViolation(affected, buildNumber),
           message("short.incompatible.since.build", affected.sinceBuild),
           message("long.incompatible.since.build", "Affected", "1.0", affected.sinceBuild, buildNumber)),
      case("until build", pluginUntilBuildConstraintViolation(affected, buildNumber),
           message("short.incompatible.until.build", affected.untilBuild),
           message("long.incompatible.until.build", "Affected", "1.0", affected.untilBuild, buildNumber)),
      case("malformed constraints", pluginMalformedSinceUntilConstraints(affected),
           message("short.failed.to.load.requirements.for.ide.version"),
           message("long.failed.to.load.requirements.for.ide.version", "Affected")),
      case("loading disabled", pluginLoadingIsDisabledCompletely(affected),
           message("short.plugin.loading.disabled"), message("long.plugin.loading.disabled", "Affected")),
      case("package prefix conflict", pluginPackagePrefixConflict(affected, affected, dependency),
           message("short.package.prefix.conflict", "Affected", "Dependency"),
           message("long.package.prefix.conflict", "Affected", "Dependency", "affected", "<none>",
                   "dependency", "<none>", "affected.prefix")),
      case("incompatible plugin", pluginIsIncompatibleWithAnotherPlugin(affected, dependency, false),
           message("short.ide.contains.conflicting.module", dependencyId),
           message("long.ide.contains.conflicting.module", "Affected", dependencyId), false),
      case("failed dependency", pluginDependencyCannotBeLoaded(affected, dependency, true),
           message("short.depends.on.failed.to.load.plugin", "Dependency"),
           message("long.depends.on.failed.to.load.plugin", "Affected", "Dependency")),
      case("absent dependency", pluginDependencyIsNotInstalled(affected, "missing", false),
           message("short.depends.on.not.installed.plugin", "missing"),
           message("long.depends.on.not.installed.plugin", "Affected", "missing"), false),
      case("explicit subset", pluginIsNotRequiredForLoadingTheExplicitlyConfiguredSubsetOfPlugins(affected),
           message("short.not.required.for.explicitly.configured.subset"),
           message("long.not.required.for.explicitly.configured.subset", "Affected"), false),
      case("conflicting ID", checkNotNull(PluginDeclaresConflictingId(
        descriptor = affected,
        declarationOrigin = affected,
        conflictingModule = dependency,
        conflictingPluginId = dependencyId,
      ).toSelectionPluginNonLoadReason()),
           message("short.declares.conflicting.id", dependencyId),
           message("long.declares.conflicting.id", "Affected", dependencyId, "Dependency")),
    )
  }

  @Test
  fun `problem kind does not infer notification eligibility`() {
    val problem = PluginLoadingProblem(
      plugin = PluginNode(PluginId.getId("affected")),
      kind = PluginLoadingProblem.Kind.ULTIMATE_DISABLED,
      shouldNotifyUser = true,
      shortMessageSupplier = { "Short" },
      detailedMessageSupplier = { "Detailed" },
    )

    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(problem.shortMessage).isEqualTo("Short")
    assertThat(problem.detailedMessage).isEqualTo("Detailed")
  }

  @Test
  fun `severity does not infer notification eligibility`() {
    for ((severity, notify) in listOf(PluginLoadingProblem.Severity.EXPECTED to true, PluginLoadingProblem.Severity.UNEXPECTED to false)) {
      val problem = PluginLoadingProblem(
        plugin = PluginNode(PluginId.getId("affected")),
        severity = severity,
        shouldNotifyUser = notify,
        shortMessageSupplier = { "Short" },
        detailedMessageSupplier = { "Detailed" },
      )

      assertThat(problem.severity).isEqualTo(severity)
      assertThat(problem.shouldNotifyUser).isEqualTo(notify)
    }
  }

  @Test
  fun `messages read the current descriptor data`() {
    val descriptor = PluginNode(PluginId.getId("affected")).apply { name = "Before" }
    val dependencyId = PluginId.getId("dependency")
    val problem = pluginDependencyIsDisabled(descriptor, dependencyId, true)

    descriptor.name = "After"

    assertThat(problem.detailedMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "After", dependencyId))
  }
}
