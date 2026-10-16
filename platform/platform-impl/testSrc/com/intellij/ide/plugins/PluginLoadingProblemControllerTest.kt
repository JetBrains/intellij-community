// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.newui.DefaultUiPluginManagerController
import com.intellij.ide.plugins.newui.PluginManagerSession
import com.intellij.ide.plugins.newui.PluginManagerSessionService
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.productMode.ProductMode
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

@TestApplication
internal class PluginLoadingProblemControllerTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var originalPluginSet: PluginSet
  private lateinit var session: PluginManagerSession

  @BeforeEach
  fun createSession() {
    originalPluginSet = PluginManagerCore.getPluginSet()
    session = PluginManagerSessionService.getInstance().createSession(UUID.randomUUID().toString())
  }

  @AfterEach
  fun restoreState() {
    PluginManagerSessionService.getInstance().removeSession(session.sessionId)
    PluginManagerCore.setPluginSet(originalPluginSet)
  }

  @Test
  fun `disabled dependency offers an Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency()

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isTrue()
    assertThat(result.requiredPluginNames).containsExactly("Root")
    assertThat(result.loadingError).isNull()
    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(result.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(result.disabledDependencyId).isEqualTo(PluginId.getId("root"))
  }

  @Test
  fun `a transitive disabled dependency offers an Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive = true)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isTrue()
    assertThat(result.requiredPluginNames).containsExactly("Root")
    assertThat(result.loadingError).isNull()
    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
  }

  @Test
  fun `a disabled affected plugin suppresses loading problems`(): Unit = checkDisabledAffectedPlugin(transitive = false)

  @Test
  fun `a disabled affected plugin suppresses transitive loading problems`(): Unit = checkDisabledAffectedPlugin(transitive = true)

  @Test
  fun `resolved session dependencies suppress the Enable suggestion`(): Unit = checkResolvedSessionDependencies(transitive = false)

  @Test
  fun `resolved transitive session dependencies suppress the Enable suggestion`(): Unit = checkResolvedSessionDependencies(transitive = true)

  @Test
  fun `disabled Ultimate supplies its classification without an Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency(rootId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
    assertThat(result.loadingError).isNotNull()
    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)
    assertThat(result.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
    assertThat(result.disabledDependencyId).isEqualTo(PluginManagerCore.ULTIMATE_PLUGIN_ID)
  }

  @Test
  fun `transitive disabled Ultimate supplies classification without an Enable suggestion`(): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive = true, rootId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.ULTIMATE_DISABLED)
    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
    assertThat(result.loadingError).isNotNull()
  }

  @Test
  fun `disabling an Ultimate dependent suppresses its page problem`(): Unit = timeoutRunBlocking {
    installDisabledDependency(rootId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString)
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.DISABLED

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(result.loadingError).isNull()
    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
  }

  @Test
  fun `resolved Ultimate dependencies suppress the page problem`(): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive = true, rootId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString)
    session.pluginStates[PluginManagerCore.ULTIMATE_PLUGIN_ID] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(result.loadingError).isNull()
    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
  }

  @TestFactory
  fun `module problems retain their IDs in page results`() = listOf(false, true).map { inaccessible ->
    dynamicTest("inaccessible=$inaccessible") {
      timeoutRunBlocking {
        val results = listOf("first", "second").map { namespace ->
          val moduleId = PluginModuleId("dependency.module", namespace)
          val pluginsDir = tempDir.resolve("$inaccessible-$namespace")
          if (inaccessible) {
            plugin("dependency") {
              name = "Dependency"
              content(namespace = namespace) {
                module(moduleId.name, loadingRule = ModuleLoadingRuleValue.REQUIRED) {
                  moduleVisibility = ModuleVisibilityValue.PRIVATE
                }
              }
            }.installAt(pluginsDir)
            session.pluginStates[PluginId.getId("dependency")] = PluginEnabledState.ENABLED
          }
          plugin("affected") {
            name = "Affected"
            dependencies { module(moduleId.name, namespace = namespace) }
          }.installAt(pluginsDir)
          val pluginSetBuilder = PluginSetTestBuilder.fromPath(pluginsDir)
          lateinit var pluginSet: PluginSet
          if (inaccessible) {
            val error = LoggedErrorProcessor.executeAndReturnLoggedError {
              pluginSet = pluginSetBuilder.build(configureClassLoaders = false)
            }
            assertThat(error).isInstanceOf(PluginException::class.java).hasMessageContaining(
              "affected isn't loaded: it depends on module 'dependency.module' which has private visibility in 'dependency' plugin",
            )
          }
          else {
            pluginSet = pluginSetBuilder.build(configureClassLoaders = false)
          }
          PluginManagerCore.setPluginSet(pluginSet)
          session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.ENABLED
          DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)

          val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

          assertThat(result.unavailableModuleId).isEqualTo(moduleId)
          assertThat(result.loadingError).isNotBlank()
          assertThat(result.suggestToEnableRequiredPlugins).isFalse()
          assertThat(result.requiredPluginNames).isEmpty()
          assertThat(Json.decodeFromString<CheckErrorsResult>(Json.encodeToString(result))).isEqualTo(result)
          assertThat(DefaultUiPluginManagerController.loadErrors(session.sessionId, listOf(PluginId.getId("affected"))))
            .containsEntry(PluginId.getId("affected"), result)
          result
        }

        assertThat(results[0].loadingError).isEqualTo(results[1].loadingError)
        assertThat(results[0]).isNotEqualTo(results[1])
      }
    }
  }

  @TestFactory
  fun `product mode exclusions retain their page classification`() = ProductMode.entries.flatMap { productMode ->
    listOf(false, true).map { transitive ->
      dynamicTest("${productMode.id}, transitive=$transitive") {
        timeoutRunBlocking {
          installProductModeDependency(productMode, transitive)

          val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

          assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.PRODUCT_MODE_MISMATCH)
          assertThat(result.severity).isEqualTo(PluginLoadingProblem.Severity.EXPECTED)
          assertThat(result.productMode).isEqualTo(productMode)
          assertThat(result.unavailableModuleId).isEqualTo(PluginModuleId("mode.module", "jetbrains"))
          assertThat(result.suggestToEnableRequiredPlugins).isFalse()
          assertThat(result.requiredPluginNames).isEmpty()
          assertThat(result.loadingError).isNotBlank()
          assertThat(DefaultUiPluginManagerController.loadErrors(session.sessionId, listOf(PluginId.getId("affected"))))
            .containsEntry(PluginId.getId("affected"), result)
        }
      }
    }
  }

  @Test
  fun `disabling a product mode dependent suppresses its page problem`(): Unit = timeoutRunBlocking {
    installProductModeDependency(ProductMode.LIGHT_MONOLITH, transitive = false)
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.DISABLED

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.loadingError).isNull()
    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
  }

  @Test
  fun `a missing side plugin is distinct from a present plugin without a problem`(): Unit = timeoutRunBlocking {
    installProductModeDependency(ProductMode.LIGHT_MONOLITH, transitive = false)
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.DISABLED

    val present = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))
    val absent = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("absent"))

    assertThat(present.pluginPresent).isTrue()
    assertThat(absent.pluginPresent).isFalse()
    assertThat(present.loadingError).isNull()
    assertThat(absent.loadingError).isNull()
  }

  private fun installProductModeDependency(productMode: ProductMode, transitive: Boolean) {
    val moduleId = PluginModuleId("mode.module", PluginModuleId.JETBRAINS_NAMESPACE)
    val pluginsDir = tempDir.resolve("${productMode.id}-$transitive")
    plugin("affected") {
      if (transitive) depends("mode-dependent")
      else content(namespace = "jetbrains") { module(moduleId.name, loadingRule = ModuleLoadingRuleValue.REQUIRED) {} }
    }.installAt(pluginsDir)
    if (transitive) {
      plugin("mode-dependent") {
        content(namespace = "jetbrains") { module(moduleId.name, loadingRule = ModuleLoadingRuleValue.REQUIRED) {} }
      }.installAt(pluginsDir)
      session.pluginStates[PluginId.getId("mode-dependent")] = PluginEnabledState.ENABLED
    }
    PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(pluginsDir)
                                    .withProductMode(productMode)
                                    .withEnvironmentConfiguredModules(mapOf(moduleId to PluginInitializationContext.EnvironmentConfiguredModuleData(
                                      UnsuitableProductModeModuleUnavailabilityReason(moduleId, productMode.id),
                                    )))
                                    .build(configureClassLoaders = false))
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)
  }

  @Test
  fun `an ordinary disabled root beside Ultimate does not offer to enable Ultimate`(): Unit = timeoutRunBlocking {
    val ultimateId = PluginManagerCore.ULTIMATE_PLUGIN_ID
    plugin("root") { name = "Root" }.installAt(tempDir)
    plugin(ultimateId.idString) { name = "Ultimate" }.installAt(tempDir)
    plugin("affected") {
      name = "Affected"
      depends("root")
      depends(ultimateId.idString)
    }.installAt(tempDir)
    PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir)
                                    .withDisabledPlugins("root", ultimateId.idString)
                                    .build(configureClassLoaders = false))
    session.pluginStates[PluginId.getId("root")] = PluginEnabledState.DISABLED
    session.pluginStates[ultimateId] = PluginEnabledState.DISABLED
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(result.loadingError).isNotNull()
    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
  }

  @Test
  fun `generic recovery enables an ordinary direct dependency`(): Unit =
    checkDependencyRecovery(transitive = false, requiresUltimate = false)

  @Test
  fun `generic recovery enables an ordinary transitive dependency`(): Unit =
    checkDependencyRecovery(transitive = true, requiresUltimate = false)

  @Test
  fun `generic recovery does not enable direct Ultimate`(): Unit = checkDependencyRecovery(transitive = false, requiresUltimate = true)

  @Test
  fun `generic recovery does not enable transitive Ultimate`(): Unit = checkDependencyRecovery(transitive = true, requiresUltimate = true)

  @Test
  fun `generic recovery enables an ordinary root without enabling Ultimate`(): Unit = timeoutRunBlocking {
    val ultimateId = PluginManagerCore.ULTIMATE_PLUGIN_ID
    plugin("root") { name = "Root" }.installAt(tempDir)
    plugin(ultimateId.idString) { name = "Ultimate" }.installAt(tempDir)
    plugin("affected") {
      name = "Affected"
      depends("root")
      depends(ultimateId.idString)
    }.installAt(tempDir)
    PluginManagerCore.setPluginSet(PluginSetTestBuilder.fromPath(tempDir)
                                    .withDisabledPlugins("root", ultimateId.idString)
                                    .build(configureClassLoaders = false))
    session.pluginStates[PluginId.getId("root")] = PluginEnabledState.DISABLED
    session.pluginStates[ultimateId] = PluginEnabledState.DISABLED
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)

    val enabledIds = DefaultUiPluginManagerController.enableRequiredPlugins(session.sessionId, PluginId.getId("affected"))

    assertThat(enabledIds).containsExactly(PluginId.getId("root"))
    assertThat(session.pluginStates[PluginId.getId("root")]).isEqualTo(PluginEnabledState.ENABLED)
    assertThat(session.pluginStates[ultimateId]).isEqualTo(PluginEnabledState.DISABLED)
    assertThat(session.statesDiff.keys.map { it.pluginId }).doesNotContain(ultimateId)
  }

  private fun checkDependencyRecovery(transitive: Boolean, requiresUltimate: Boolean): Unit = timeoutRunBlocking {
    val rootId = if (requiresUltimate) PluginManagerCore.ULTIMATE_PLUGIN_ID else PluginId.getId("root")
    installDisabledDependency(transitive, rootId.idString)

    val enabledIds = DefaultUiPluginManagerController.enableRequiredPlugins(session.sessionId, PluginId.getId("affected"))

    if (requiresUltimate) {
      assertThat(enabledIds).isEmpty()
      assertThat(session.pluginStates[rootId]).isEqualTo(PluginEnabledState.DISABLED)
      assertThat(session.statesDiff.keys.map { it.pluginId }).doesNotContain(rootId)
    }
    else {
      assertThat(enabledIds).containsExactly(rootId)
      assertThat(session.pluginStates[rootId]).isEqualTo(PluginEnabledState.ENABLED)
    }
  }

  private fun checkDisabledAffectedPlugin(transitive: Boolean): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive)
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.DISABLED

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
    assertThat(result.loadingError).isNull()
  }

  private fun checkResolvedSessionDependencies(transitive: Boolean): Unit = timeoutRunBlocking {
    installDisabledDependency(transitive)
    session.pluginStates[PluginId.getId("root")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)

    val result = DefaultUiPluginManagerController.getErrors(session.sessionId, PluginId.getId("affected"))

    assertThat(result.suggestToEnableRequiredPlugins).isFalse()
    assertThat(result.requiredPluginNames).isEmpty()
    assertThat(result.loadingError).isNull()
  }

  private fun installDisabledDependency(transitive: Boolean = false, rootId: String = "root") {
    plugin(rootId) { name = "Root" }.installAt(tempDir)
    if (transitive) {
      plugin("intermediate") {
        name = "Intermediate"
        depends(rootId)
      }.installAt(tempDir)
      session.pluginStates[PluginId.getId("intermediate")] = PluginEnabledState.ENABLED
    }
    plugin("affected") {
      name = "Affected"
      depends(if (transitive) "intermediate" else rootId)
    }.installAt(tempDir)
    val pluginSet = PluginSetTestBuilder.fromPath(tempDir)
      .withDisabledPlugins(rootId)
      .build(configureClassLoaders = false)
    PluginManagerCore.setPluginSet(pluginSet)
    session.pluginStates[PluginId.getId(rootId)] = PluginEnabledState.DISABLED
    session.pluginStates[PluginId.getId("affected")] = PluginEnabledState.ENABLED
    DefaultUiPluginManagerController.updatePluginDependenciesSync(session.sessionId)
  }
}
