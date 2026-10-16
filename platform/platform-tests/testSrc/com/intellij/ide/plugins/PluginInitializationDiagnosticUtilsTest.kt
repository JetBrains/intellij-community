// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleVisibilityValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.content
import com.intellij.platform.testFramework.plugins.dependencies
import com.intellij.platform.testFramework.plugins.depends
import com.intellij.platform.testFramework.plugins.installAt
import com.intellij.platform.testFramework.plugins.module
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.rules.InMemoryFsExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class PluginInitializationDiagnosticUtilsTest {
  init {
    Logger.setFactory(TestLoggerFactory::class.java)
    Logger.setUnitTestMode()
    PluginManagerCore.isUnitTestMode = true
  }

  @RegisterExtension
  @JvmField
  val inMemoryFs = InMemoryFsExtension()

  private val pluginsDirPath get() = inMemoryFs.fs.getPath("/wd/plugins")

  private class RecordingLogger : Logger() {
    var infoMessage: String? = null

    override fun isDebugEnabled(): Boolean = false

    override fun debug(message: String?, t: Throwable?) {}

    override fun info(message: String?, t: Throwable?) {
      check(infoMessage == null)
      infoMessage = message
    }

    override fun warn(message: String?, t: Throwable?) {
      error("Unexpected warning: $message", t)
    }

    override fun error(message: String?, t: Throwable?, vararg details: String?) {
      throw AssertionError("Unexpected error: $message", t)
    }
  }

  @Nested
  inner class MajorProblems {
    private val ultimateId = PluginManagerCore.ULTIMATE_PLUGIN_ID.idString

    private fun buildPluginSet(vararg disabledPluginIds: String): PluginSet {
      return PluginSetTestBuilder.fromPath(pluginsDirPath)
        .withDisabledPlugins(*disabledPluginIds)
        .build(configureClassLoaders = false)
    }

    private fun collectProblems(pluginSet: PluginSet): List<String> {
      return PluginInitializationDiagnosticUtils.collectMajorPluginLoadingProblemMessages(pluginSet)
    }

    @Test
    fun `missing dependency is logged as a concise plugin problem`() {
      plugin("broken") { depends("missing") }.installAt(pluginsDirPath)
      val logger = RecordingLogger()

      PluginInitializationDiagnosticUtils.logMajorPluginLoadingProblems(
        logger = logger,
        pluginSet = buildPluginSet(),
        reportingPolicy = PluginLoadingErrorReportingPolicy(PluginLoadingErrorLogLevel.INFO, reportToUser = false),
      )

      assertThat(logger.infoMessage)
        .startsWith("Problems found while loading plugins:\n  ")
        .contains("plugin 'broken'", "plugin missing", "absent")
    }

    @Test
    fun `a failed required module does not make its plugin require itself`() {
      plugin("makefile") {
        name = "Makefile"
        version = "1.0"
        content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
          module("makefile.core", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("missing.runner") }
          }
        }
      }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).containsExactly(
        "plugin 'Makefile' (makefile, 1.0) cannot be loaded: " +
        "module makefile.core depends on module missing.runner which is absent"
      )
    }

    @Test
    fun `an internal module chain reports its terminal failure without a self dependency`() {
      plugin("broken") {
        version = "1.0"
        content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
          module("broken.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("broken.bridge") }
          }
          module("broken.bridge") {
            dependencies { module("missing.module") }
          }
        }
      }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).containsExactly(
        "plugin 'broken' (broken, 1.0) cannot be loaded: " +
        "module broken.bridge depends on module missing.module which is absent"
      )
    }

    @Test
    fun `a failed module in another plugin retains its owner even when plugin names match`() {
      plugin("provider") {
        name = "Same Name"
        version = "1.0"
        content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
          module("provider.required", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("missing.module") }
          }
        }
      }.installAt(pluginsDirPath)
      plugin("dependent") {
        name = "Same Name"
        version = "2.0"
        depends("provider")
      }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).hasSize(2)
      assertThat(messages).contains(
        "plugin 'Same Name' (dependent, 2.0) requires plugin 'Same Name' (provider, 1.0): " +
        "module provider.required depends on module missing.module which is absent"
      )
    }

    @Test
    fun `disabled plugin is omitted but its dependent is reported`() {
      plugin("dependency") {}.installAt(pluginsDirPath)
      plugin("dependent") { depends("dependency") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet("dependency"))

      assertThat(messages).hasSize(1)
      assertThat(messages.single()).contains("plugin 'dependent'", "requires plugin 'dependency'", "to be enabled")
    }

    @ParameterizedTest
    @EnumSource(PluginLoadingErrorLogLevel::class)
    fun `disabled Ultimate and its dependents produce no major problem log`(logLevel: PluginLoadingErrorLogLevel) {
      plugin(ultimateId) {}.installAt(pluginsDirPath)
      plugin("direct") { depends(ultimateId) }.installAt(pluginsDirPath)
      plugin("transitive") { depends("direct") }.installAt(pluginsDirPath)
      val pluginSet = buildPluginSet(ultimateId)
      val logger = RecordingLogger()

      assertThat(collectProblems(pluginSet)).isEmpty()
      PluginInitializationDiagnosticUtils.logMajorPluginLoadingProblems(
        logger = logger,
        pluginSet = pluginSet,
        reportingPolicy = PluginLoadingErrorReportingPolicy(logLevel, reportToUser = false),
      )

      assertThat(logger.infoMessage).isNull()
    }

    @Test
    fun `required module chains rooted in disabled Ultimate are not major problems`() {
      plugin(ultimateId) {
        content(namespace = PluginModuleId.JETBRAINS_NAMESPACE) {
          module("ultimate.module") { moduleVisibility = ModuleVisibilityValue.PUBLIC }
        }
      }.installAt(pluginsDirPath)
      plugin("intermediate") {
        content {
          module("required.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {
            dependencies { module("ultimate.module", namespace = PluginModuleId.JETBRAINS_NAMESPACE) }
          }
        }
      }.installAt(pluginsDirPath)
      plugin("dependent") { depends("intermediate") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet(ultimateId))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `an alias of disabled Ultimate is not a major problem`() {
      plugin(ultimateId) { pluginAliases = listOf("ultimate.alias") }.installAt(pluginsDirPath)
      plugin("dependent") { depends("ultimate.alias") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet(ultimateId))

      assertThat(messages).isEmpty()
    }

    @Test
    fun `disabled Ultimate exclusions retain ordinary disabled and missing dependency problems`() {
      plugin(ultimateId) {}.installAt(pluginsDirPath)
      plugin("expected") { depends(ultimateId) }.installAt(pluginsDirPath)
      plugin("ordinary") {}.installAt(pluginsDirPath)
      plugin("ordinary-dependent") { depends("ordinary") }.installAt(pluginsDirPath)
      plugin("unrelated") { depends("missing") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet(ultimateId, "ordinary"))

      assertThat(messages).hasSize(2)
      assertThat(messages).anySatisfy {
        assertThat(it).contains("plugin 'ordinary-dependent'", "requires plugin 'ordinary'", "to be enabled")
      }
      assertThat(messages).anySatisfy {
        assertThat(it).contains("plugin 'unrelated'", "plugin missing", "absent")
      }
      assertThat(messages).allSatisfy { assertThat(it).doesNotContain(ultimateId, "plugin 'expected'") }
    }

    @Test
    fun `another recorded failure remains a major problem in a plugin that requires disabled Ultimate`() {
      plugin(ultimateId) {}.installAt(pluginsDirPath)
      plugin("dependent") {
        depends("missing")
        depends(ultimateId)
      }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet(ultimateId))

      assertThat(messages).hasSize(1)
      assertThat(messages.single()).contains("plugin 'dependent'", "plugin missing", "absent")
    }

    @Test
    fun `missing Ultimate remains a major problem`() {
      plugin("dependent") { depends(ultimateId) }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).hasSize(1)
      assertThat(messages.single()).contains("plugin 'dependent'", "plugin $ultimateId", "absent")
    }

    @Test
    fun `failed Ultimate remains a major problem for its dependents`() {
      plugin(ultimateId) { depends("missing") }.installAt(pluginsDirPath)
      plugin("dependent") { depends(ultimateId) }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).hasSize(2)
      assertThat(messages).anySatisfy { assertThat(it).contains("plugin 'dependent'", ultimateId, "absent") }
      assertThat(messages).allSatisfy { assertThat(it).contains("plugin missing", "absent") }
    }

    @Test
    fun `disabled Ultimate and its dependents remain in the INFO resolution log`() {
      plugin(ultimateId) {}.installAt(pluginsDirPath)
      plugin("direct") { depends(ultimateId) }.installAt(pluginsDirPath)
      plugin("transitive") { depends("direct") }.installAt(pluginsDirPath)
      val logger = RecordingLogger()

      PluginInitializationDiagnosticUtils.logExclusionTree(logger, buildPluginSet(ultimateId))

      assertThat(logger.infoMessage)
        .startsWith("Plugin set resolution:\n")
        .contains(ultimateId, "is marked disabled", "plugin 'direct'", "plugin 'transitive'")
    }

    @Test
    fun `dependency cycle is reported once without graph details`() {
      plugin("first") { depends("second") }.installAt(pluginsDirPath)
      plugin("second") { depends("first") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).hasSize(1)
      assertThat(messages.single())
        .startsWith("Dependency cycle detected between ")
        .contains("first", "second")
        .doesNotContain("depends on:")
    }

    @Test
    fun `descriptor reading errors are reported`() {
      plugin("broken") {
        content {
          module("duplicate.module") { isSeparateJar = true }
          module("duplicate.module") { packagePrefix = "duplicate.module" }
        }
      }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages).hasSize(1)
      assertThat(messages.single()).contains("Failed to read a plugin descriptor", "broken")
    }

    @Test
    fun `incompatible plugins are reported`() {
      plugin("incompatible") { untilBuild = "100.*" }.installAt(pluginsDirPath)
      val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath)
        .withProductBuildNumber(BuildNumber.fromString("250.0")!!)
        .build(configureClassLoaders = false)

      val messages = collectProblems(pluginSet)

      assertThat(messages).hasSize(1)
      assertThat(messages.single()).contains("plugin 'incompatible'", "incompatible with the product")
    }

    @Test
    fun `plugin id conflicts are reported`() {
      plugin("first") { pluginAliases = listOf("conflicting.id") }.installAt(pluginsDirPath)
      plugin("second") { pluginAliases = listOf("conflicting.id") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet())

      assertThat(messages)
        .hasSize(2)
        .allSatisfy { assertThat(it).contains("declares conflicting id", "conflicting.id") }
    }

    @Test
    fun `environment configuration exclusions are not reported transitively`() {
      plugin("environment-dependent") {
        content {
          module("environment.module", loadingRule = ModuleLoadingRuleValue.REQUIRED) {}
        }
      }.installAt(pluginsDirPath)
      plugin("transitive-dependent") { depends("environment-dependent") }.installAt(pluginsDirPath)
      val environmentModuleId = PluginModuleId("environment.module", PluginModuleId.JETBRAINS_NAMESPACE)
      val pluginSet = PluginSetTestBuilder.fromPath(pluginsDirPath)
        .withEnvironmentConfiguredModules(
          mapOf(
            environmentModuleId to PluginInitializationContext.EnvironmentConfiguredModuleData(
              UnsuitableProductModeModuleUnavailabilityReason(environmentModuleId, "test")
            )
          )
        )
        .build(configureClassLoaders = false)

      val messages = collectProblems(pluginSet)

      assertThat(messages).isEmpty()
    }
  }
}
