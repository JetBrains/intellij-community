// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.codeInsight

import com.intellij.compose.ide.plugin.shared.COMMON_MAIN
import com.intellij.compose.ide.plugin.shared.ComposeBaseTestCase
import com.intellij.compose.ide.plugin.shared.DESKTOP_MAIN
import com.intellij.compose.ide.plugin.shared.WASM_JS_MAIN
import com.intellij.openapi.application.ex.PathManagerEx
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/** The test data root of this module, relative to the community home. */
internal const val COMPOSE_CODE_INSIGHT_TEST_DATA_RELATIVE_PATH: String =
  "plugins/compose/intellij.compose.ide.plugin.codeInsight/testData"

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ValueSource(strings = [COMMON_MAIN, DESKTOP_MAIN, WASM_JS_MAIN])
annotation class ComposeCodeInsightAllSourceSets

/**
 * The base class for a code insight test on a Gradle project in the test data of this module.
 * A subclass resolves the name of its project against [testDataProjectRoot].
 */
abstract class ComposeCodeInsightTestCase : ComposeBaseTestCase() {
  override val testDataProjectRoot: Path
    get() = Path.of(PathManagerEx.getCommunityHomePath(), COMPOSE_CODE_INSIGHT_TEST_DATA_RELATIVE_PATH)
}
