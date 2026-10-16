// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.newui.MyPluginModel
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.productMode.ProductMode
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginLoadingProblemPresentationTest {
  @Test
  fun `a disabled Ultimate problem stays outside the generic error area`() {
    val result = CheckErrorsResult(loadingError = "Ultimate is disabled", isUltimateDisabledProblem = true)

    assertThat(MyPluginModel.getErrors(result)).isEmpty()
  }

  @Test
  fun `an ordinary loading problem retains its error text`() {
    val result = CheckErrorsResult(loadingError = "Missing dependency", isDisabledDependencyError = true)

    assertThat(MyPluginModel.getErrors(result).map { it.toString() }).containsExactly("<span>Missing dependency</span>")
  }

  @Test
  fun `an ordinary disabled dependency retains its Enable link`() {
    val result = CheckErrorsResult(requiredPluginNames = setOf("Root"), suggestToEnableRequiredPlugins = true)

    val errors = MyPluginModel.getErrors(result)

    assertThat(errors).hasSize(2)
    assertThat(errors.last().toString()).contains("href=\"link\"")
  }

  @Test
  fun `the page result retains Ultimate classification through serialization`() {
    val result = CheckErrorsResult(loadingError = "Ultimate is disabled", isUltimateDisabledProblem = true)

    assertThat(Json.decodeFromString<CheckErrorsResult>(Json.encodeToString(result))).isEqualTo(result)
  }

  @Test
  fun `a page result without Ultimate classification keeps its default`() {
    val result = Json.decodeFromString<CheckErrorsResult>("""{"loadingError":"Missing dependency","isDisabledDependencyError":true}""")

    assertThat(result.isUltimateDisabledProblem).isFalse()
    assertThat(MyPluginModel.getErrors(result)).hasSize(1)
    assertThat(result.kind).isEqualTo(PluginLoadingProblem.Kind.OTHER)
    assertThat(result.severity).isEqualTo(PluginLoadingProblem.Severity.UNEXPECTED)
    assertThat(result.disabledDependencyId).isNull()
  }

  @Test
  fun `the page result retains structured classification through serialization`() {
    val result = CheckErrorsResult(
      loadingError = "Ultimate is disabled",
      isUltimateDisabledProblem = true,
      kind = PluginLoadingProblem.Kind.ULTIMATE_DISABLED,
      severity = PluginLoadingProblem.Severity.EXPECTED,
      disabledDependencyId = PluginId.getId("com.intellij.ultimate"),
    )

    assertThat(Json.decodeFromString<CheckErrorsResult>(Json.encodeToString(result))).isEqualTo(result)
  }

  @Test
  fun `an expected product mode problem stays outside the generic error area`() {
    val result = CheckErrorsResult(
      loadingError = "This plugin does not support Light mode",
      isDisabledDependencyError = true,
      kind = PluginLoadingProblem.Kind.PRODUCT_MODE_MISMATCH,
      severity = PluginLoadingProblem.Severity.EXPECTED,
      productMode = ProductMode.LIGHT_REMOTE,
      unavailableModuleName = "mode.module",
      unavailableModuleNamespace = "jetbrains",
    )

    assertThat(MyPluginModel.getErrors(result)).isEmpty()
    assertThat(Json.decodeFromString<CheckErrorsResult>(Json.encodeToString(result))).isEqualTo(result)
  }

  @Test
  fun `the page result retains independent side data through serialization`() {
    val local = CheckErrorsResult(
      loadingError = "Missing dependency",
      isDisabledDependencyError = true,
      shouldNotifyUser = true,
      detailedMessage = "A dependency is missing",
      dependencyPluginId = PluginId.getId("dependency"),
    )
    val remote = CheckErrorsResult(pluginPresent = false)
    val result = CheckErrorsResult(problemsBySide = mapOf(PluginSource.LOCAL to local, PluginSource.REMOTE to remote))

    assertThat(Json.decodeFromString<CheckErrorsResult>(Json.encodeToString(result))).isEqualTo(result)
  }
}
