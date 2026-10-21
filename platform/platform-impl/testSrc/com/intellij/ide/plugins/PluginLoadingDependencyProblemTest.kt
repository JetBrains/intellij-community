// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.diagnostic.PluginException
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
import com.intellij.testFramework.LoggedErrorProcessor
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
  fun `an ambiguous installed alias uses its first excluded owner`() {
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

    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "First")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("shared.alias")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("first"))
  }

  @Test
  fun `a bundled dependency and its excluded update use the first excluded descriptor`() {
    val pluginSet = buildPluginSetWithBundledUpdate(
      plugin("dependency") {
        name = "Bundled Dependency"
        version = "1.0"
        pluginAliases = listOf("shared.alias")
      },
      plugin("dependency") {
        name = "Updated Dependency"
        version = "2.0"
        pluginAliases = listOf("shared.alias")
      },
      plugin("conflicting") {
        pluginAliases = listOf("shared.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
    )

    val descriptors = pluginSet.input.discoveryResult.pluginLists.single().plugins.filter { it.pluginId.idString == "dependency" }
    assertThat(descriptors).hasSize(2)
    assertThat(descriptors.map { it.isBundled }).containsExactly(true, false)
    assertThat(pluginSet.excludedFromCandidateSubset.keys).containsAll(descriptors)
    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(PluginId.getId("dependency"))).isNull()
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Bundled Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `an alias removed by an update retains its excluded bundled owner`() {
    val pluginSet = buildPluginSetWithBundledUpdate(
      plugin("dependency") {
        name = "Bundled Dependency"
        version = "1.0"
        pluginAliases = listOf("dependency.alias")
      },
      plugin("dependency") {
        name = "Updated Dependency"
        version = "2.0"
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency.alias")
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.name }).contains("Updated Dependency")
    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(PluginId.getId("dependency.alias"))).isNull()
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Bundled Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency.alias")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `an excluded content module alias retains its plugin owner`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        sinceBuild = "264"
        pluginAliases = listOf("shared.alias")
        content(namespace = "jetbrains") {
          module("dependency.module") {
            pluginAliases = listOf("dependency.alias")
          }
        }
      },
      plugin("preferred") {
        pluginAliases = listOf("shared.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency.alias")
      },
    )

    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(PluginId.getId("dependency.alias"))).isNull()
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency.alias")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `a disabled alias displays its owner name and retains the recovery ID`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Friendly Dependency"
        pluginAliases = listOf("dependency.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency.alias")
      },
      configure = { withDisabledPlugins("dependency") },
    )

    val state = PluginManagerStateService().calculateState(
      pluginSet, PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    )
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])
    assertThat(problem.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Friendly Dependency"))
    assertThat(problem.detailedMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Affected", "Friendly Dependency"))
    assertThat(problem.disabledDependencyId).isEqualTo(PluginId.getId("dependency"))
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(PluginId.getId("dependency") to "Friendly Dependency"))
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("affected") to "Affected"))
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
  fun `a missing module has an unavailable module explanation`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      dependencies { module("missing.module") }
    })

    val problem = assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "jetbrains")))
    assertThat(problem.dependencyPluginId).isNull()
  }

  @Test
  fun `a missing module retains its explicit namespace`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      dependencies { module("missing.module", namespace = "vendor") }
    })

    val problem = assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "vendor")))
    assertThat(problem.dependencyPluginId).isNull()
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

    val problem = assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "jetbrains")))
    assertThat(problem.dependencyPluginId).isNull()
  }

  @Test
  fun `an invisible module reports an access restriction`() {
    val pluginSet = buildPluginSetWithVisibilityError(
      "affected isn't loaded: it depends on module 'dependency.private' which has private visibility in 'dependency' plugin",
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
    val problem = assertDependencyProblem(pluginSet, "inaccessible", "dependency.private", "Dependency", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("dependency.private", "jetbrains")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `a required module reports an internal dependency from another namespace`() {
    val pluginSet = buildPluginSetWithVisibilityError(
      "affected.required isn't loaded: it is from namespace 'other' and depends on module 'dependency.internal' " +
      "which is registered in 'dependency' plugin with internal visibility in namespace 'vendor'",
      plugin("dependency") {
        name = "Dependency"
        content(namespace = "vendor") {
          module("dependency.internal") {
            moduleVisibility = ModuleVisibilityValue.INTERNAL
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        content(namespace = "other") {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("dependency.internal", namespace = "vendor") }
          }
        }
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("dependency"))
    val problem = assertDependencyProblem(pluginSet, "inaccessible", "dependency.internal", "Dependency", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("dependency.internal", "vendor")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `an internal dependency from the same namespace does not produce a problem`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        content(namespace = "vendor") {
          module("dependency.internal") {
            moduleVisibility = ModuleVisibilityValue.INTERNAL
          }
        }
      },
      plugin("affected") {
        content(namespace = "vendor") {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("dependency.internal", namespace = "vendor") }
          }
        }
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("affected"), PluginId.getId("dependency"))
    val state = PluginManagerStateService().calculateState(
      pluginSet, PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    )
    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `a visibility failure in a direct dependency names that dependency`() {
    val pluginSet = buildPluginSetWithVisibilityError(
      "dependency isn't loaded: it depends on module 'owner.private' which has private visibility in 'owner' plugin",
      plugin("owner") {
        name = "Owner"
        content(namespace = "jetbrains") {
          module("owner.private") {}
        }
      },
      plugin("dependency") {
        name = "Dependency"
        dependencies { module("owner.private") }
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("owner"))
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `a visibility failure through several plugins names the plugin with the access problem`() {
    val pluginSet = buildPluginSetWithVisibilityError(
      "dependency isn't loaded: it depends on module 'owner.private' which has private visibility in 'owner' plugin",
      plugin("owner") {
        name = "Owner"
        content(namespace = "jetbrains") {
          module("owner.private") {}
        }
      },
      plugin("dependency") {
        name = "Dependency"
        dependencies { module("owner.private") }
      },
      plugin("intermediate") {
        name = "Intermediate"
        depends("dependency")
      },
      plugin("affected") {
        name = "Affected"
        depends("intermediate")
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("owner"))
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `a visibility failure in a required module names its owning plugin`() {
    val pluginSet = buildPluginSetWithVisibilityError(
      "dependency.required isn't loaded: it is from namespace 'other' and depends on module 'owner.internal' " +
      "which is registered in 'owner' plugin with internal visibility in namespace 'vendor'",
      plugin("owner") {
        name = "Owner"
        content(namespace = "vendor") {
          module("owner.internal", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            moduleVisibility = ModuleVisibilityValue.INTERNAL
          }
        }
      },
      plugin("dependency") {
        name = "Dependency"
        content(namespace = "other") {
          module("dependency.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("owner.internal", namespace = "vendor") }
          }
        }
      },
      plugin("intermediate") {
        name = "Intermediate"
        depends("dependency")
      },
      plugin("affected") {
        name = "Affected"
        content(namespace = "jetbrains") {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { plugin("intermediate") }
          }
        }
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("owner"))
    val dependency = pluginSet.allPlugins.single { it.pluginId.idString == "dependency" }
    assertThat(pluginSet.resolvedPluginSet.getExclusionReason(dependency.contentModules.single()))
      .isInstanceOf(DependencyIsNotVisible::class.java)
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  @TestFor(issues = ["IJPL-246315"])
  fun `a failed core module reports its missing module`() {
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

    val problem = assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "jetbrains")))
    assertThat(problem.dependencyPluginId).isNull()
  }

  @Test
  fun `a missing module remains the explanation through intermediate plugins`() {
    val pluginSet = buildPluginSet(
      plugin("root") {
        name = "Root"
        dependencies { module("missing.module") }
      },
      plugin("intermediate") {
        name = "Intermediate"
        depends("root")
      },
      plugin("affected") {
        name = "Affected"
        depends("intermediate")
      },
    )

    val problem = assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "jetbrains")))
    assertThat(problem.dependencyPluginId).isNull()
  }

  @Test
  fun `a required module retains a missing module across a plugin boundary`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        dependencies { module("missing.module", namespace = "vendor") }
      },
      plugin("affected") {
        name = "Affected"
        content(namespace = "jetbrains") {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { plugin("dependency") }
          }
        }
      },
    )

    val problem = assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("missing.module", "vendor")))
    assertThat(problem.dependencyPluginId).isNull()
  }

  @Test
  fun `a missing plugin across a plugin boundary still names the failed dependency`() {
    val pluginSet = buildPluginSet(
      plugin("dependency") {
        name = "Dependency"
        depends("missing")
      },
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
    )

    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Dependency")
    assertThat(problem.dependency).isEqualTo(DependencyRef.Plugin(PluginId.getId("dependency")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("dependency"))
  }

  @Test
  fun `a failed module across plugin boundaries retains the first required module`() {
    val pluginSet = buildPluginSet(
      plugin("root") {
        name = "Root"
        depends("missing")
      },
      plugin("intermediate") {
        name = "Intermediate"
        content(namespace = "vendor") {
          module("intermediate.bridge") {
            moduleVisibility = ModuleVisibilityValue.PUBLIC
            dependencies { plugin("root") }
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        content(namespace = "jetbrains") {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("intermediate.bridge", namespace = "vendor") }
          }
        }
      },
    )

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("intermediate"))
    val problem = assertDependencyProblem(pluginSet, "failed.to.load", "Intermediate")
    assertThat(problem.dependency).isEqualTo(DependencyRef.ContentModule(PluginModuleId("intermediate.bridge", "vendor")))
    assertThat(problem.dependencyPluginId).isEqualTo(PluginId.getId("intermediate"))
  }

  @Test
  fun `an optional module with a missing dependency does not exclude its owner`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      content(namespace = "jetbrains") {
        module("affected.optional") {
          dependencies { module("missing.module") }
        }
      }
    })

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("affected"))
    val state = PluginManagerStateService().calculateState(
      pluginSet, PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    )
    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `a missing module problem clears when its provider becomes available`() {
    val pluginSet = buildPluginSet(plugin("affected") {
      name = "Affected"
      dependencies { module("missing.module") }
    })
    assertDependencyProblem(pluginSet, "unavailable", "missing.module", dependencyType = "module")

    plugin("provider") {
      content(namespace = "jetbrains") {
        module("missing.module") {
          moduleVisibility = ModuleVisibilityValue.PUBLIC
        }
      }
    }.installAt(tempDir)
    val replacement = buildPluginSet()
    assertThat(replacement.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("affected"), PluginId.getId("provider"))
    val state = PluginManagerStateService().calculateState(
      replacement, PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    )
    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  private fun assertDependencyProblem(
    pluginSet: PluginSet,
    messageKind: String,
    vararg dependencyText: String,
    dependencyType: String = "plugin",
  ): PluginLoadingProblem {
    val state = PluginManagerStateService().calculateState(
      pluginSet, PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true),
    )
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])
    assertThat(problem.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.$messageKind.$dependencyType", *dependencyText))
    assertThat(problem.detailedMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.long.depends.on.$messageKind.$dependencyType", "Affected", *dependencyText))
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

  private fun buildPluginSetWithVisibilityError(expectedMessage: String, vararg plugins: PluginSpec): PluginSet {
    lateinit var pluginSet: PluginSet
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      pluginSet = buildPluginSet(*plugins)
    }
    assertThat(error).isInstanceOf(PluginException::class.java).hasMessageContaining(expectedMessage)
    return pluginSet
  }

  private fun buildPluginSet(vararg plugins: PluginSpec, configure: PluginSetTestBuilder.() -> Unit = {}): PluginSet {
    plugins.forEach { it.installAt(tempDir) }
    return PluginSetTestBuilder.fromPath(tempDir)
      .withProductBuildNumber(checkNotNull(BuildNumber.fromString("IU-263.1")))
      .apply(configure)
      .build(configureClassLoaders = false)
  }

  private fun buildPluginSetWithBundledUpdate(
    bundledPlugin: PluginSpec,
    updatedPlugin: PluginSpec,
    vararg plugins: PluginSpec,
  ): PluginSet {
    val bundledPath = bundledPlugin.installAt(tempDir.resolve("bundled"))
    val updatedPath = updatedPlugin.installAt(tempDir.resolve("updates"))
    val otherPaths = plugins.map { it.installAt(tempDir) }
    return PluginSetTestBuilder.fromDescriptors { loadingContext ->
      listOf(
        checkNotNull(loadDescriptorFromFileOrDirInTests(bundledPath, loadingContext, isBundled = true)),
        checkNotNull(loadDescriptorFromFileOrDirInTests(updatedPath, loadingContext, isBundled = false)),
      ) + otherPaths.map { checkNotNull(loadDescriptorFromFileOrDirInTests(it, loadingContext, isBundled = false)) }
    }
      .withProductBuildNumber(checkNotNull(BuildNumber.fromString("IU-263.1")))
      .build(configureClassLoaders = false)
  }
}
