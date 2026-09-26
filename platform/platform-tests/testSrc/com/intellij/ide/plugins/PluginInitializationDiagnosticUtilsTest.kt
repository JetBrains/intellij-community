// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import com.intellij.platform.pluginSystem.testFramework.PluginSetTestBuilder
import com.intellij.platform.testFramework.plugins.content
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
    fun `disabled plugin is omitted but its dependent is reported`() {
      plugin("dependency") {}.installAt(pluginsDirPath)
      plugin("dependent") { depends("dependency") }.installAt(pluginsDirPath)

      val messages = collectProblems(buildPluginSet("dependency"))

      assertThat(messages).hasSize(1)
      assertThat(messages.single()).contains("plugin 'dependent'", "requires plugin 'dependency'", "to be enabled")
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
