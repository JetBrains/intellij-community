// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.idea.TestFor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.productMode.ProductMode
import com.intellij.platform.testFramework.plugins.PluginSpec
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.lang.UrlClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@TestApplication
internal class PluginManagerStateServiceTest {
  private val service = PluginManagerStateService()

  @TempDir
  lateinit var tempDir: Path

  private lateinit var originalPluginSet: PluginSet

  @BeforeEach
  fun rememberOriginalPluginSet() {
    originalPluginSet = PluginManagerCore.getPluginSet()
  }

  @AfterEach
  fun restoreOriginalPluginSet() {
    PluginManagerCore.setPluginSet(originalPluginSet)
  }

  @Test
  @TestFor(issues = ["IJPL-183884"])
  fun `loading error is cleared when disabled dependency and dependent are loaded`() {
    val dependency = plugin("bar") { name = "Bar" }
    val dependent = plugin("foo") {
      name = "Foo"
      depends("bar")
    }
    install(dependency, dependent)

    val initialPluginSet = buildPluginSet(disabledPluginIds = arrayOf("bar"))
    val initialState = publishAndGetState(initialPluginSet)
    val dependentId = PluginId.getId("foo")
    val dependencyId = PluginId.getId("bar")
    val reason = initialState.pluginNonLoadReasons[dependentId]

    assertThat(reason!!.disabledDependencyId).isEqualTo(dependencyId)
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Bar"))
    assertThat(initialState.pluginLoadingProblemsForNotification).containsExactly(reason.detailedMessage)
    assertThat(initialState.pluginsToEnable).containsExactlyEntriesOf(mapOf(dependencyId to "Bar"))
    assertThat(initialState.pluginsToDisable).containsExactlyEntriesOf(mapOf(dependentId to "Foo"))

    val loadedPluginSet = buildPluginSet()
    val loadedState = publishAndGetState(loadedPluginSet)

    assertThat(loadedState).isNotSameAs(initialState)
    assertThat(loadedPluginSet.enabledPlugins.map { it.pluginId }).contains(dependencyId, dependentId)
    assertThat(loadedState.pluginNonLoadReasons).isEmpty()
    assertThat(loadedState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(loadedState.pluginsToEnable).isEmpty()
    assertThat(loadedState.pluginsToDisable).isEmpty()
  }

  @Test
  @TestFor(issues = ["IJPL-246315", "IDEA-392708"])
  fun `transitive disabled dependency reports the disabled root`() {
    install(
      plugin("root") { name = "Root" },
      plugin("intermediate") {
        name = "Intermediate"
        depends("root")
      },
      plugin("affected") {
        name = "Affected"
        depends("intermediate")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val rootId = PluginId.getId("root")
    val intermediateId = PluginId.getId("intermediate")
    val affectedId = PluginId.getId("affected")
    val affectedReason = state.pluginNonLoadReasons[affectedId]
    val intermediateReason = state.pluginNonLoadReasons[intermediateId]

    assertThat(affectedReason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(intermediateReason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(affectedReason.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Root"))
    assertThat(intermediateReason.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Root"))
    assertThat(state.pluginLoadingProblemsForNotification).containsExactlyInAnyOrder(
      CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Affected", "Root"),
      CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Intermediate", "Root"),
    )
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyInAnyOrderEntriesOf(mapOf(affectedId to "Affected", intermediateId to "Intermediate"))

    val loadedPluginSet = buildPluginSet()
    val loadedState = publishAndGetState(loadedPluginSet)

    assertThat(loadedPluginSet.enabledPlugins.map { it.pluginId }).contains(rootId, intermediateId, affectedId)
    assertThat(loadedState.pluginNonLoadReasons).isEmpty()
    assertThat(loadedState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(loadedState.pluginsToEnable).isEmpty()
    assertThat(loadedState.pluginsToDisable).isEmpty()
  }

  @Test
  fun `several intermediate plugins report one disabled root`() {
    install(plugin("root") { name = "Root" })
    for (index in 0..2) {
      install(plugin("dependent$index") {
        name = "Dependent $index"
        depends(if (index == 0) "root" else "dependent${index - 1}")
      })
    }

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val rootId = PluginId.getId("root")

    assertThat(state.pluginNonLoadReasons).hasSize(3)
    for (problem in state.pluginNonLoadReasons.values) {
      assertThat(problem.disabledDependencyId).isEqualTo(rootId)
      assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Root"))
    }
    assertThat(state.pluginLoadingProblemsForNotification).containsExactlyInAnyOrderElementsOf(
      (0..2).map { CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Dependent $it", "Root") },
    )
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
  }

  @Test
  fun `a required content module reports the transitive disabled root`() {
    install(
      plugin("root") { name = "Root" },
      plugin("intermediate") {
        name = "Intermediate"
        depends("root")
      },
      plugin("affected") {
        name = "Affected"
        content {
          module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { plugin("intermediate") }
          }
        }
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val rootId = PluginId.getId("root")
    val reason = state.pluginNonLoadReasons[PluginId.getId("affected")]

    assertThat(reason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Root"))
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
  }

  @Test
  @TestFor(issues = ["IJPL-246315"])
  fun `a core content module reports the disabled root instead of IDEA CORE`() {
    install(
      plugin("root") { name = "Root" },
      plugin(PluginManagerCore.CORE_PLUGIN_ID) {
        name = "IDEA CORE"
        content(namespace = "jetbrains") {
          module("core.bridge") {
            moduleVisibility = ModuleVisibilityValue.PUBLIC
            dependencies { plugin("root") }
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        dependencies { module("core.bridge") }
      },
    )

    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("root"))
    val state = publishAndGetState(pluginSet)
    val rootId = PluginId.getId("root")
    val affectedId = PluginId.getId("affected")
    val reason = state.pluginNonLoadReasons[affectedId]

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginManagerCore.CORE_ID)
    assertThat(pluginSet.resolvedPluginSet.getExclusionReason(pluginSet.allPlugins.single { it.pluginId == rootId }))
      .isInstanceOf(PluginIsMarkedDisabled::class.java)
    assertThat(state.pluginNonLoadReasons).containsOnlyKeys(affectedId)
    assertThat(reason!!.disabledDependencyId).isEqualTo(rootId)
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", "Root"))
    assertThat(state.pluginLoadingProblemsForNotification)
      .containsExactly(CoreBundle.message("plugin.loading.error.long.depends.on.disabled.plugin", "Affected", "Root"))
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(rootId to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(affectedId to "Affected"))
  }

  @Test
  fun `a root that is not disabled keeps the failed intermediate explanation`() {
    install(
      plugin("root") {
        name = "Root"
        depends("missing")
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

    val state = publishAndGetState(buildPluginSet())
    val reason = state.pluginNonLoadReasons[PluginId.getId("affected")]

    assertThat(reason!!.disabledDependencyId).isNull()
    assertThat(reason.shortMessage)
      .isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.failed.to.load.plugin", "Intermediate"))
    assertThat(state.pluginsToEnable).isEmpty()
  }

  @Test
  fun `snapshot is cached for the same plugin set and reporting policy`() {
    install(plugin("foo") {
      name = "Foo"
      depends("missing")
    })
    PluginManagerCore.setPluginSet(buildPluginSet())

    val first = service.getCurrentState(USER_REPORTING_POLICY)
    val second = service.getCurrentState(USER_REPORTING_POLICY)

    assertThat(second).isSameAs(first)
  }

  @Test
  fun `reporting policy only suppresses notification messages`() {
    install(plugin("foo") {
      name = "Foo"
      depends("missing")
    })
    PluginManagerCore.setPluginSet(buildPluginSet())

    val visibleState = checkNotNull(service.getCurrentState(USER_REPORTING_POLICY))
    val hiddenState = checkNotNull(service.getCurrentState(SILENT_REPORTING_POLICY))

    assertThat(visibleState.pluginLoadingProblemsForNotification).isNotEmpty()
    assertThat(hiddenState).isNotSameAs(visibleState)
    assertThat(hiddenState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(hiddenState.pluginNonLoadReasons.keys).isEqualTo(visibleState.pluginNonLoadReasons.keys)
    assertThat(hiddenState.pluginNonLoadReasons.mapValues { it.value.disabledDependencyId })
      .isEqualTo(visibleState.pluginNonLoadReasons.mapValues { it.value.disabledDependencyId })
    assertThat(hiddenState.pluginNonLoadReasons.mapValues { it.value.shouldNotifyUser })
      .isEqualTo(visibleState.pluginNonLoadReasons.mapValues { it.value.shouldNotifyUser })
    assertThat(reasonMessages(hiddenState)).isEqualTo(reasonMessages(visibleState))
    assertThat(hiddenState.pluginsToEnable).isEqualTo(visibleState.pluginsToEnable)
    assertThat(hiddenState.pluginsToDisable).isEqualTo(visibleState.pluginsToDisable)
  }

  @Test
  fun `global messages precede plugin problems and follow the reporting policy`() {
    install(
      plugin("broken-descriptor") {
        content {
          module("duplicate.module") { isSeparateJar = true }
          module("duplicate.module") { packagePrefix = "duplicate.module" }
        }
      },
      plugin("cycle.a") {
        name = "Cycle A"
        depends("cycle.b")
      },
      plugin("cycle.b") {
        name = "Cycle B"
        depends("cycle.a")
      },
      plugin("dependency") { name = "Dependency" },
      plugin("affected") {
        name = "Affected"
        depends("dependency")
      },
    )
    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf("dependency"))

    val visibleState = service.calculateState(pluginSet, USER_REPORTING_POLICY)
    val hiddenState = service.calculateState(pluginSet, SILENT_REPORTING_POLICY)

    val descriptorError = pluginSet.input.discoveryResult.descriptorLoadingErrors.single()
    val descriptorMessage = CoreBundle.message("plugin.loading.error.text.file.contains.invalid.plugin.descriptor",
                                               PluginUtils.pluginPathToUserString(descriptorError.path))
    val messages = visibleState.pluginLoadingProblemsForNotification
    assertThat(messages).hasSize(3)
    val cycleMessage = messages[1]
    assertThat(cycleMessage).contains("Cycle A", "Cycle B")
    val problem = checkNotNull(visibleState.pluginNonLoadReasons[PluginId.getId("affected")])
    assertThat(messages).containsExactly(descriptorMessage, cycleMessage, problem.detailedMessage)
    assertThat(visibleState.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("affected"))
    assertThat(visibleState.pluginsToEnable).containsExactlyEntriesOf(mapOf(PluginId.getId("dependency") to "Dependency"))
    assertThat(visibleState.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("affected") to "Affected"))
    assertThat(hiddenState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(reasonMessages(hiddenState)).isEqualTo(reasonMessages(visibleState))
    assertThat(hiddenState.pluginsToEnable).isEqualTo(visibleState.pluginsToEnable)
    assertThat(hiddenState.pluginsToDisable).isEqualTo(visibleState.pluginsToDisable)
  }

  @Test
  fun `selection problems notify without adding recovery targets`() {
    install(
      plugin("selected-out") {
        name = "Selected out"
        sinceBuild = "264"
        pluginAliases = listOf("shared.alias")
      },
      plugin("preferred") {
        pluginAliases = listOf("shared.alias")
      },
      plugin("resolved") {
        name = "Resolved"
        depends("missing")
      },
    )
    val pluginSet = PluginSetTestBuilder.fromPath(tempDir)
      .withProductBuildNumber(checkNotNull(BuildNumber.fromString("IU-263.1")))
      .build(configureClassLoaders = false)

    val state = publishAndGetState(pluginSet)
    val selectedOutId = PluginId.getId("selected-out")
    val resolvedId = PluginId.getId("resolved")
    val selectionProblem = checkNotNull(state.pluginNonLoadReasons[selectedOutId])
    val resolvedProblem = checkNotNull(state.pluginNonLoadReasons[resolvedId])

    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(selectedOutId)).isNull()
    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(resolvedId)).isNotNull()
    assertThat(state.pluginLoadingProblemsForNotification)
      .containsExactlyInAnyOrder(selectionProblem.detailedMessage, resolvedProblem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(resolvedId to "Resolved"))
  }

  @Test
  fun `optional module conflicts retain the last problem and a Disable target`() {
    install(plugin("affected") {
      name = "Affected"
      content {
        for (index in 0..2) {
          module("affected.module$index", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {
            packagePrefix = "shared.prefix"
          }
        }
      }
    })
    val pluginSet = buildPluginSet()
    val affectedId = PluginId.getId("affected")
    val affected = checkNotNull(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(affectedId)).getMainDescriptor()
    val conflicts = affected.contentModules.mapNotNull {
      pluginSet.resolvedPluginSet.getExclusionReason(it) as? PackagePrefixConflictWithAnotherModule
    }

    val state = publishAndGetState(pluginSet)
    val problem = checkNotNull(state.pluginNonLoadReasons[affectedId])

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(affectedId)
    assertThat(conflicts).hasSize(2)
    val lastConflict = conflicts.last()
    val expectedProblem = pluginPackagePrefixConflict(affected, lastConflict.descriptor, lastConflict.preferredConflictingModule)
    assertThat(state.pluginNonLoadReasons).containsOnlyKeys(affectedId)
    assertThat(problem.shortMessage).isEqualTo(expectedProblem.shortMessage)
    assertThat(problem.detailedMessage).isEqualTo(expectedProblem.detailedMessage)
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(affectedId to "Affected"))
  }

  @Test
  fun `missing dependency is exposed as a reason and disable suggestion`() {
    install(plugin("foo") {
      name = "Foo"
      depends("missing")
    })

    val state = publishAndGetState(buildPluginSet())
    val pluginId = PluginId.getId("foo")
    val reason = state.pluginNonLoadReasons[pluginId]

    assertThat(reason!!.disabledDependencyId).isNull()
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(reason.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(reason.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(pluginId to "Foo"))
    assertThat(service.getPluginNonLoadReason(pluginId)).isSameAs(reason)
  }

  @Test
  fun `implementation detail errors are retained but not reported to the user`() {
    install(plugin("foo") {
      name = "Foo"
      implementationDetail = true
      depends("missing")
    })

    val state = publishAndGetState(buildPluginSet())
    val pluginId = PluginId.getId("foo")
    val reason = state.pluginNonLoadReasons[pluginId]

    assertThat(reason!!.shouldNotifyUser).isFalse()
    assertThat(reason.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
  }

  @Test
  fun `explicitly disabled plugin has no loading error or suggested action`() {
    install(plugin("foo") { name = "Foo" })

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("foo")))

    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @TestFactory
  fun `configuration exclusions stay expected through required plugins and modules`() = listOf(
    NonBundledPluginsLoadingIsDisabled,
    PluginIsNotContainedInTheExplicitlyConfiguredSubsetOfPluginsForLoading,
    PluginLoadingIsDisabledCompletelyExceptCore,
  ).map { reason ->
    dynamicTest(reason.getLogMessage()) {
      install(
        plugin("root") { name = "Root" },
        plugin("intermediate") { depends("root") },
        plugin("affected") {
          content {
            module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
              dependencies { plugin("intermediate") }
            }
          }
        },
        plugin("optional") {
          content {
            module("optional.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {
              dependencies { plugin("root") }
            }
          }
        },
        plugin("unrelated") { depends("missing") },
      )
      val builder = PluginSetTestBuilder.fromPath(tempDir)
      val initContext = object : PluginInitializationContext by builder.buildInitContext() {
        override fun provideModuleExclusionsImposedByProductRules(pluginSet: UnambiguousPluginSet) =
          sequenceOf(checkNotNull(pluginSet.resolvePluginId(PluginId.getId("root"))) to reason)
      }
      val pluginSet = PluginManagerCore.initializePlugins(
        initContext = initContext,
        discoveredPlugins = builder.discoverPlugins().second,
        coreLoader = UrlClassLoader.build().get(),
        parentActivity = null,
        configureClassLoaders = false,
      )
      val state = publishAndGetState(pluginSet)
      val unrelatedId = PluginId.getId("unrelated")

      assertThat(state.pluginNonLoadReasons.keys.map { it.idString })
        .containsExactlyInAnyOrder("root", "intermediate", "affected", "unrelated")
      for (id in listOf("root", "intermediate", "affected")) {
        val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId(id)])
        assertThat(problem.plugin.pluginId.idString).isEqualTo(id)
        assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
        assertThat(problem.shouldNotifyUser).isFalse()
        assertThat(problem.disabledDependencyId).isNull()
        assertThat(problem.shortMessage).isEqualTo(state.pluginNonLoadReasons[PluginId.getId("root")]!!.shortMessage)
      }
      assertThat(pluginSet.enabledPlugins.map { it.pluginId.idString }).containsExactly("optional")
      val unrelated = checkNotNull(state.pluginNonLoadReasons[unrelatedId])
      assertThat(unrelated.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
      assertThat(state.pluginLoadingProblemsForNotification).containsExactly(unrelated.detailedMessage)
      assertThat(state.pluginsToDisable).containsOnlyKeys(unrelatedId)
      assertThat(state.pluginsToEnable).isEmpty()

      val loadedState = publishAndGetState(buildPluginSet())

      assertThat(loadedState.pluginNonLoadReasons).containsOnlyKeys(unrelatedId)
      assertThat(loadedState.pluginLoadingProblemsForNotification).containsExactly(unrelated.detailedMessage)
    }
  }

  @TestFactory
  fun `a direct product mode exclusion has an expected explanation`() = ProductMode.entries.map { productMode ->
    dynamicTest(productMode.id) {
      install(modeDependentPlugin())

      val pluginSet = buildProductModePluginSet(productMode)
      val state = publishAndGetState(pluginSet)

      assertThat(pluginSet.enabledPlugins.map { it.pluginId }).doesNotContain(PluginId.getId("mode-dependent"))
      assertThat(state.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("mode-dependent"))
      assertProductModeProblem(checkNotNull(state.pluginNonLoadReasons[PluginId.getId("mode-dependent")]), productMode)
      assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
      assertThat(state.pluginsToEnable).isEmpty()
      assertThat(state.pluginsToDisable).isEmpty()
    }
  }

  @TestFactory
  @TestFor(issues = ["IJPL-249174", "IJPL-258176"])
  fun `a transitive product mode exclusion retains the expected root`() = ProductMode.entries.map { productMode ->
    dynamicTest(productMode.id) {
      install(
        modeDependentPlugin(),
        plugin("intermediate") {
          name = "Intermediate"
          depends("mode-dependent")
        },
        plugin("affected") {
          name = "Affected"
          content {
            module("affected.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
              dependencies { plugin("intermediate") }
            }
          }
        },
      )

      val state = publishAndGetState(buildProductModePluginSet(productMode))
      val intermediateId = PluginId.getId("intermediate")
      val affectedId = PluginId.getId("affected")

      assertThat(state.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("mode-dependent"), intermediateId, affectedId)
      for (problem in state.pluginNonLoadReasons.values) {
        assertProductModeProblem(problem, productMode)
      }
      assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
      assertThat(state.pluginsToEnable).isEmpty()
      assertThat(state.pluginsToDisable).isEmpty()
    }
  }

  @TestFactory
  fun `a core product mode exclusion retains the expected root`() = ProductMode.entries.map { productMode ->
    dynamicTest(productMode.id) {
      install(
        plugin(PluginManagerCore.CORE_PLUGIN_ID) {
          name = "IDEA CORE"
          content(namespace = "jetbrains") {
            module(MODE_MODULE_ID.name) { moduleVisibility = ModuleVisibilityValue.PUBLIC }
          }
        },
        plugin("affected") {
          name = "Affected"
          dependencies { module(MODE_MODULE_ID.name) }
        },
      )

      val pluginSet = buildProductModePluginSet(productMode)
      val state = publishAndGetState(pluginSet)
      val affectedId = PluginId.getId("affected")
      val problem = checkNotNull(state.pluginNonLoadReasons[affectedId])

      assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginManagerCore.CORE_ID).doesNotContain(affectedId)
      assertThat(state.pluginNonLoadReasons).containsOnlyKeys(affectedId)
      assertProductModeProblem(problem, productMode)
      assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
      assertThat(state.pluginsToDisable).isEmpty()
    }
  }

  @TestFactory
  fun `an optional product mode module does not produce a loading problem`() = ProductMode.entries.map { productMode ->
    dynamicTest(productMode.id) {
      install(
        modeDependentPlugin(ModuleLoadingRuleValue.OPTIONAL),
        plugin("affected") { depends("mode-dependent") },
      )

      val pluginSet = buildProductModePluginSet(productMode)
      val state = publishAndGetState(pluginSet)

      assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("mode-dependent"), PluginId.getId("affected"))
      assertThat(state.pluginNonLoadReasons).isEmpty()
      assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
      assertThat(state.pluginsToEnable).isEmpty()
      assertThat(state.pluginsToDisable).isEmpty()
    }
  }

  @TestFactory
  fun `a product mode exclusion does not suppress an unrelated missing dependency`() = ProductMode.entries.map { productMode ->
    dynamicTest(productMode.id) {
      install(
        modeDependentPlugin(),
        plugin("unrelated") {
          name = "Unrelated"
          depends("missing")
        },
      )

      val state = publishAndGetState(buildProductModePluginSet(productMode))
      val unrelatedId = PluginId.getId("unrelated")
      val problem = checkNotNull(state.pluginNonLoadReasons[unrelatedId])

      assertThat(state.pluginNonLoadReasons).containsOnlyKeys(unrelatedId, PluginId.getId("mode-dependent"))
      assertProductModeProblem(checkNotNull(state.pluginNonLoadReasons[PluginId.getId("mode-dependent")]), productMode)
      assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
      assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
      assertThat(problem.shouldNotifyUser).isTrue()
      assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin",
                                                                    "missing"))
      assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
      assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(unrelatedId to "Unrelated"))
    }
  }

  @TestFactory
  fun `making a product mode module available removes its dependent loading problem`() = ProductMode.entries.map { productMode ->
    dynamicTest(productMode.id) {
      install(modeDependentPlugin(), plugin("affected") { depends("mode-dependent") })
      val initialState = publishAndGetState(buildProductModePluginSet(productMode))
      assertThat(initialState.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("mode-dependent"), PluginId.getId("affected"))

      val loadedPluginSet = buildProductModePluginSet(ProductMode.MONOLITH, moduleAvailable = true)
      val loadedState = publishAndGetState(loadedPluginSet)

      assertThat(loadedState).isNotSameAs(initialState)
      assertThat(loadedPluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("mode-dependent"), PluginId.getId("affected"))
      assertThat(loadedState.pluginNonLoadReasons).isEmpty()
      assertThat(loadedState.pluginLoadingProblemsForNotification).isEmpty()
      assertThat(loadedState.pluginsToEnable).isEmpty()
      assertThat(loadedState.pluginsToDisable).isEmpty()
    }
  }

  @Test
  fun `disabled Ultimate keeps the dependency problem but suppresses notification text`() {
    installUltimateDependency()

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.disabledDependencyId).isEqualTo(ULTIMATE_ID)
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
    assertThat(problem.shouldNotifyUser).isFalse()
    assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.disabled.plugin", ULTIMATE_ID))
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `a transitive disabled Ultimate dependency suppresses notification text`() {
    installUltimateDependency(transitive = true)

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))

    assertThat(state.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("affected"), PluginId.getId("intermediate"))
    for (problem in state.pluginNonLoadReasons.values) {
      assertThat(problem.disabledDependencyId).isEqualTo(ULTIMATE_ID)
      assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)
      assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
      assertThat(problem.shouldNotifyUser).isFalse()
    }
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `a required content module preserves the disabled Ultimate root`() {
    installUltimateDependency(transitive = true, loadingRule = ModuleLoadingRuleValue.REQUIRED)

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.disabledDependencyId).isEqualTo(ULTIMATE_ID)
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
    assertThat(problem.shouldNotifyUser).isFalse()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `mixed notification data excludes hidden Ultimate recovery targets`() {
    installUltimateDependency()
    install(plugin("unrelated") {
      name = "Unrelated"
      depends("missing")
    })

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val unrelatedProblem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("unrelated")])

    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(unrelatedProblem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("unrelated") to "Unrelated"))
  }

  @Test
  fun `mixed disabled roots offer only ordinary recovery targets`() {
    installUltimateDependency(transitive = true)
    install(
      plugin("root") { name = "Root" },
      plugin("ordinary") {
        name = "Ordinary"
        depends("root")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString, "root")))
    val ordinaryProblem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("ordinary")])

    assertThat(state.pluginNonLoadReasons).hasSize(3)
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(ordinaryProblem.detailedMessage)
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(PluginId.getId("root") to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("ordinary") to "Ordinary"))
  }

  @Test
  fun `a hidden problem does not add targets beside a visible problem with the same root`() {
    install(
      plugin("root") { name = "Root" },
      plugin("hidden") {
        name = "Hidden"
        implementationDetail = true
        depends("root")
      },
      plugin("visible") {
        name = "Visible"
        depends("root")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))
    val visibleProblem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("visible")])

    assertThat(state.pluginNonLoadReasons).hasSize(2)
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(visibleProblem.detailedMessage)
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(PluginId.getId("root") to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("visible") to "Visible"))
  }

  @Test
  fun `a hidden disabled dependency problem has no recovery targets`() {
    install(
      plugin("root") { name = "Root" },
      plugin("hidden") {
        name = "Hidden"
        implementationDetail = true
        depends("root")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root")))

    assertThat(state.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("hidden"))
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `disabled Ultimate suppresses notification of an unrelated missing dependency`() {
    install(
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        depends(ULTIMATE_ID.idString)
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.disabledDependencyId).isNull()
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `a transitive Ultimate requirement suppresses an unrelated notification`() {
    install(
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("intermediate") {
        name = "Intermediate"
        depends(ULTIMATE_ID.idString)
      },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        depends("intermediate")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(state.pluginNonLoadReasons).hasSize(2)
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `an Ultimate alias suppresses an unrelated notification`() {
    install(
      plugin(ULTIMATE_ID.idString) {
        name = "Ultimate"
        pluginAliases = listOf("ultimate.alias")
      },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        depends("ultimate.alias")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `an Ultimate module requirement suppresses an unrelated notification`() {
    install(
      plugin(ULTIMATE_ID.idString) {
        name = "Ultimate"
        content(namespace = "jetbrains") {
          module("ultimate.module", loadingRule = ModuleLoadingRuleValue.OPTIONAL) {
            moduleVisibility = ModuleVisibilityValue.PUBLIC
          }
        }
      },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        dependencies { module("ultimate.module") }
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @TestFactory
  fun `only required content modules impose the Ultimate notification restriction`() =
    listOf(ModuleLoadingRuleValue.REQUIRED to true, ModuleLoadingRuleValue.OPTIONAL to false).map { (loadingRule, suppressNotification) ->
      dynamicTest(loadingRule.name) {
        val pluginDir = tempDir.resolve(loadingRule.name)
        listOf(
          plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
          plugin("affected") {
            name = "Affected"
            depends("missing")
            content {
              module("affected.module", loadingRule = loadingRule) {
                dependencies { plugin(ULTIMATE_ID.idString) }
              }
            }
          },
        ).forEach { it.installAt(pluginDir) }

        val pluginSet = PluginSetTestBuilder.fromPath(pluginDir)
          .withDisabledPlugins(ULTIMATE_ID.idString)
          .build(configureClassLoaders = false)
        val state = publishAndGetState(pluginSet)
        val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

        assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
        if (suppressNotification) {
          assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
          assertThat(state.pluginsToDisable).isEmpty()
        }
        else {
          assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
          assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("affected") to "Affected"))
        }
        assertThat(state.pluginsToEnable).isEmpty()
      }
    }

  @Test
  fun `an optional Ultimate dependency does not suppress an unrelated notification`() {
    install(
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        depends(ULTIMATE_ID.idString, optional = true)
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("affected") to "Affected"))
  }

  @Test
  fun `disabled Ultimate suppresses a selection failure notification`() {
    install(
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("affected") {
        name = "Affected"
        sinceBuild = "264"
        pluginAliases = listOf("shared.alias")
        depends(ULTIMATE_ID.idString)
      },
      plugin("preferred") {
        name = "Preferred"
        pluginAliases = listOf("shared.alias")
      },
    )
    val pluginSet = PluginSetTestBuilder.fromPath(tempDir)
      .withDisabledPlugins(ULTIMATE_ID.idString)
      .withProductBuildNumber(checkNotNull(BuildNumber.fromString("IU-263.1")))
      .build(configureClassLoaders = false)

    val state = publishAndGetState(pluginSet)
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(pluginSet.resolvedPluginSet.candidateSet.resolvePluginId(PluginId.getId("affected"))).isNull()
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `enabling Ultimate restores notification of a remaining failure`() {
    install(
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        depends(ULTIMATE_ID.idString)
      },
    )
    val initialState = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    assertThat(initialState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(initialState.pluginsToDisable).isEmpty()

    val state = publishAndGetState(buildPluginSet())
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.not.installed.plugin", "missing"))
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("affected") to "Affected"))
  }

  @Test
  fun `Ultimate notification suppression uses the supplied plugin set`() {
    install(
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("affected") {
        name = "Affected"
        depends("missing")
        depends(ULTIMATE_ID.idString)
      },
    )
    val restrictedPluginSet = buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString))
    val unrestrictedPluginSet = buildPluginSet()
    PluginManagerCore.setPluginSet(unrestrictedPluginSet)

    val restrictedState = service.calculateState(restrictedPluginSet, USER_REPORTING_POLICY)

    assertThat(restrictedState.pluginNonLoadReasons).containsOnlyKeys(PluginId.getId("affected"))
    assertThat(restrictedState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(restrictedState.pluginsToDisable).isEmpty()

    PluginManagerCore.setPluginSet(restrictedPluginSet)
    val unrestrictedState = service.calculateState(unrestrictedPluginSet, USER_REPORTING_POLICY)
    val problem = checkNotNull(unrestrictedState.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(unrestrictedState.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(unrestrictedState.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("affected") to "Affected"))
  }

  @Test
  fun `enabled Ultimate leaves no dependency problem`() {
    installUltimateDependency()

    val pluginSet = buildPluginSet()
    val state = publishAndGetState(pluginSet)

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(ULTIMATE_ID, PluginId.getId("affected"))
    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `absent Ultimate is reported as a missing dependency`() {
    install(plugin("affected") {
      name = "Affected"
      depends(ULTIMATE_ID.idString)
    })

    val state = publishAndGetState(buildPluginSet())
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.disabledDependencyId).isNull()
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
  }

  @Test
  fun `an optional Ultimate module does not exclude its main plugin`() {
    installUltimateDependency(loadingRule = ModuleLoadingRuleValue.OPTIONAL)

    val pluginSet = buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString))
    val state = publishAndGetState(pluginSet)

    assertThat(pluginSet.enabledPlugins.map { it.pluginId }).contains(PluginId.getId("affected"))
    assertThat(state.pluginNonLoadReasons).isEmpty()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `failed Ultimate is not a disabled Ultimate problem`() {
    install(
      plugin(ULTIMATE_ID.idString) {
        name = "Ultimate"
        depends("missing")
      },
      plugin("affected") {
        name = "Affected"
        depends(ULTIMATE_ID.idString)
      },
    )

    val state = publishAndGetState(buildPluginSet())
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(problem.disabledDependencyId).isNull()
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(problem.shortMessage).isEqualTo(CoreBundle.message("plugin.loading.error.short.depends.on.failed.to.load.plugin", "Ultimate"))
    assertThat(state.pluginLoadingProblemsForNotification).contains(problem.detailedMessage)
    assertThat(state.pluginsToEnable).isEmpty()
  }

  @Test
  fun `disabled Ultimate suppresses notification but retains an ordinary disabled root`() {
    install(
      plugin("root") { name = "Root" },
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("affected") {
        name = "Affected"
        depends("root")
        depends(ULTIMATE_ID.idString)
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root", ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("affected")])

    assertThat(problem.disabledDependencyId).isEqualTo(PluginId.getId("root"))
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(problem.shouldNotifyUser).isTrue()
    assertThat(state.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(state.pluginsToEnable).isEmpty()
    assertThat(state.pluginsToDisable).isEmpty()
  }

  @Test
  fun `an unrestricted plugin retains recovery beside a restricted plugin with the same root`() {
    install(
      plugin("root") { name = "Root" },
      plugin(ULTIMATE_ID.idString) { name = "Ultimate" },
      plugin("restricted") {
        name = "Restricted"
        depends("root")
        depends(ULTIMATE_ID.idString)
      },
      plugin("unrestricted") {
        name = "Unrestricted"
        depends("root")
      },
    )

    val state = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf("root", ULTIMATE_ID.idString)))
    val problem = checkNotNull(state.pluginNonLoadReasons[PluginId.getId("unrestricted")])

    assertThat(state.pluginNonLoadReasons).hasSize(2)
    assertThat(state.pluginLoadingProblemsForNotification).containsExactly(problem.detailedMessage)
    assertThat(state.pluginsToEnable).containsExactlyEntriesOf(mapOf(PluginId.getId("root") to "Root"))
    assertThat(state.pluginsToDisable).containsExactlyEntriesOf(mapOf(PluginId.getId("unrestricted") to "Unrestricted"))
  }

  @Test
  fun `an Ultimate problem disappears when its dependency becomes available`() {
    installUltimateDependency(transitive = true)
    val initialState = publishAndGetState(buildPluginSet(disabledPluginIds = arrayOf(ULTIMATE_ID.idString)))
    assertThat(initialState.pluginNonLoadReasons.values.map { it.kind }).containsOnly(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)

    val loadedState = publishAndGetState(buildPluginSet())

    assertThat(loadedState.pluginNonLoadReasons).isEmpty()
    assertThat(loadedState.pluginLoadingProblemsForNotification).isEmpty()
    assertThat(loadedState.pluginsToEnable).isEmpty()
    assertThat(loadedState.pluginsToDisable).isEmpty()
  }

  private fun installUltimateDependency(transitive: Boolean = false, loadingRule: ModuleLoadingRuleValue? = null) {
    install(plugin(ULTIMATE_ID.idString) { name = "Ultimate" })
    if (transitive) {
      install(plugin("intermediate") {
        name = "Intermediate"
        depends(ULTIMATE_ID.idString)
      })
    }
    val dependencyId = if (transitive) "intermediate" else ULTIMATE_ID.idString
    install(plugin("affected") {
      name = "Affected"
      if (loadingRule == null) {
        depends(dependencyId)
      }
      else {
        content {
          module("affected.module", loadingRule = loadingRule) {
            dependencies { plugin(dependencyId) }
          }
        }
      }
    })
  }

  private fun install(vararg plugins: PluginSpec) {
    plugins.forEach { it.installAt(tempDir) }
  }

  private fun modeDependentPlugin(loadingRule: ModuleLoadingRuleValue = ModuleLoadingRuleValue.REQUIRED): PluginSpec =
    plugin("mode-dependent") {
      name = "Mode dependent"
      content(namespace = "jetbrains") {
        module(MODE_MODULE_ID.name, loadingRule = loadingRule) {}
      }
    }

  private fun assertProductModeProblem(problem: PluginLoadingProblem, productMode: ProductMode) {
    assertThat(problem.kind).isEqualTo(PluginLoadingProblem.Kind.PRODUCT_MODE_MISMATCH)
    assertThat(problem.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
    assertThat(problem.shouldNotifyUser).isFalse()
    assertThat(problem.disabledDependencyId).isNull()
    assertThat(problem.productMode).isEqualTo(productMode)
    assertThat(problem.unavailableModuleId).isEqualTo(MODE_MODULE_ID)
    val expectedText = when (productMode) {
      ProductMode.LIGHT_REMOTE, ProductMode.LIGHT_WITH_RD_CONNECTION, ProductMode.LIGHT_MONOLITH -> "This plugin does not support Light mode"
      ProductMode.FRONTEND -> "This plugin does not support frontend mode"
      ProductMode.BACKEND -> "This plugin does not support backend mode"
      ProductMode.MONOLITH, ProductMode.LANGUAGE_SERVER -> "This plugin does not support the current product mode"
    }
    assertThat(problem.shortMessage).isEqualTo(expectedText)
    assertThat(problem.detailedMessage).isEqualTo(expectedText)
  }

  private fun buildProductModePluginSet(productMode: ProductMode, moduleAvailable: Boolean = false): PluginSet {
    val reason = if (moduleAvailable) null else UnsuitableProductModeModuleUnavailabilityReason(MODE_MODULE_ID, productMode.id)
    return PluginSetTestBuilder.fromPath(tempDir)
      .withProductMode(productMode)
      .withEnvironmentConfiguredModules(mapOf(MODE_MODULE_ID to PluginInitializationContext.EnvironmentConfiguredModuleData(reason)))
      .build(configureClassLoaders = false)
  }

  private fun buildPluginSet(disabledPluginIds: Array<String> = emptyArray()): PluginSet {
    return PluginSetTestBuilder.fromPath(tempDir)
      .withDisabledPlugins(*disabledPluginIds)
      .build(configureClassLoaders = false)
  }

  private fun publishAndGetState(pluginSet: PluginSet): PluginManagerStateSnapshot {
    PluginManagerCore.setPluginSet(pluginSet)
    return checkNotNull(service.getCurrentState(USER_REPORTING_POLICY))
  }

  private fun reasonMessages(state: PluginManagerStateSnapshot): Map<PluginId, Pair<String, String>> =
    state.pluginNonLoadReasons.mapValues { it.value.shortMessage to it.value.detailedMessage }

  private companion object {
    val MODE_MODULE_ID = PluginModuleId("mode.module", PluginModuleId.JETBRAINS_NAMESPACE)
    val ULTIMATE_ID = PluginManagerCore.ULTIMATE_PLUGIN_ID
    val USER_REPORTING_POLICY = PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = true)
    val SILENT_REPORTING_POLICY = USER_REPORTING_POLICY.copy(reportToUser = false)
  }
}
