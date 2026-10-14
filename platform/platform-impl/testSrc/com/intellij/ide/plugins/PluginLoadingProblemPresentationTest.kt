// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.newui.MyPluginModel
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
  }
}
