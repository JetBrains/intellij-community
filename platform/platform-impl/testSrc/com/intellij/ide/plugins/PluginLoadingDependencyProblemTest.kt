// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.idea.TestFor
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.PluginSpec
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class PluginLoadingDependencyProblemTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `an absent plugin uses the installation message`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      depends("missing")
    })

    val problem = assertDependencyProblem(pluginSet, "not.installed", "missing")
    assertThat(problem.disabledDependencyId).isNull()
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("missing")))
  }

  @Test
  fun `an absent modern plugin dependency uses the installation message`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      dependencies { plugin("missing") }
    })

    val problem = assertDependencyProblem(pluginSet, "not.installed", "missing")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("missing")))
  }

  @Test
  @TestFor(issues = ["IJPL-252363"])
  fun `an incompatible installed dependency uses its name`() {
    val pluginSet = buildPluginSet(
      incompatibleDependency(),
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
    )

    assertThat(pluginSet.input.discoveryResult.pluginLists.single().plugins.map { it.pluginId })
      .contains(PluginId.getId("dependency"))
    assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
  }

  @Test
  @TestFor(issues = ["IJPL-252363"])
  fun `an installed dependency excluded by an ID conflict reports a failed plugin`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        sinceBuild = "264"
        pluginAliases = listOf("shared.alias")
      },
      plugin("preferred") {
        name = "Preferred"
        pluginAliases = listOf("shared.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
    )

    assertThat(pluginSet.input.discoveryResult.pluginLists.single().plugins.map { it.pluginId })
      .contains(PluginId.getId("dependency"))
    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(PluginId.getId("dependency"))).isNull()
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `an incompatible installed alias reports its failed owner`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        sinceBuild = "264"
        pluginAliases = listOf("dependency.alias", "shared.alias")
      },
      plugin("preferred") {
        name = "Preferred"
        pluginAliases = listOf("shared.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency.alias")
      },
    )

    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency.alias")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `an ambiguous installed alias keeps its ID as a failed dependency`() {
    val pluginSet = buildPluginSet(
      plugin("first") {
        name = "First"
        pluginAliases = listOf("shared.alias")
      },
      plugin("second") {
        name = "Second"
        pluginAliases = listOf("shared.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("shared.alias")
      },
    )

    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "shared.alias")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("shared.alias")))
  }

  @Test
  fun `a broken installed dependency uses its name`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        version = "1.0"
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
      configure = { withBrokenPlugin("dependency", "1.0") },
    )

    assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
  }

  @Test
  fun `a missing module uses the plugin installation message`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      dependencies { module("missing.module") }
    })

    val problem = assertDependencyProblem(pluginSet, "not.installed", "missing.module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "jetbrains")))
  }

  @Test
  fun `a required module keeps its missing dependency explanation`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      content(namespace = "jetbrains") {
        module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
          dependencies { module("missing.module") }
        }
      }
    })

    val problem = assertDependencyProblem(pluginSet, "not.installed", "missing.module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "jetbrains")))
  }

  @Test
  fun `an invisible module uses its owner in the installation message`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        content(namespace = "jetbrains") {
          module("dependency.private", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            moduleVisibility = ModuleVisibilityValue.PRIVATE
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        dependencies { module("dependency.private") }
      },
    )

    val affected = pluginSet.allPlugins.single { it.pluginId.idString == "affected" }
    assertThat(pluginSet.resolvedPluginSet.getExclusionReason(affected)).isInstanceOf(DependencyIsNotVisible::class.java)
    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("dependency"))
    val problem = assertDependencyProblem(pluginSet, "not.installed", "dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("dependency.private", "jetbrains")))
  }

  @Test
  @TestFor(issues = ["IJPL-246315"])
  fun `a failed core module uses IDEA CORE as a failed plugin`() {
    val pluginSet = buildPluginSet(
      plugin(PluginManagerCore.CORE_PLUGIN_ID) {
        name = "IDEA CORE"
        content(namespace = "jetbrains") {
          module("core.bridge") {
            moduleVisibility = ModuleVisibilityValue.PUBLIC
            dependencies { module("missing.module") }
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        dependencies { module("core.bridge") }
      },
    )

    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "IDEA CORE")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("core.bridge", "jetbrains")))
  }

  private fun assertDependencyProblem(pluginSet: PluginSet, messageKind: String, dependencyText: String): PluginLoadingProblem {
    val state = PluginManagerStateService().calculateState(
      pluginSet, PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    )
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])
    assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.$messageKind.plugin", dependencyText))
    assertThat(problem.detailedMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.long.depends.on.$messageKind.plugin", "Affected", dependencyText))
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).contains(problem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsEntry(PluginId.getId("affected"), "Affected")
    return problem
  }

  private fun incompatibleDependency(): PluginSpec = plugin("dependency") {
    name = "Dependency"
    sinceBuild = "264"
  }

  private fun buildPluginSet(vararg plugins: PluginSpec, configure: PluginSetTestBuilder.() -> Unit = {}): PluginSet {
    plugins.forEach { it.installAt(tempDir) }
    return PluginSetTestBuilder.fromPath(tempDir)
      .withProductBuildNumber(checkNotNull(BuildNumber.fromString("IU-263.1")))
      .apply(configure)
      .build(configureClassLoaders = false)
  }
}
