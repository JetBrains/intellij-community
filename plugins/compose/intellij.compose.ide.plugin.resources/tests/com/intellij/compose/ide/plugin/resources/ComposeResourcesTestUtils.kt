// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.application.ex.PathManagerEx
import java.nio.file.Path

/**
 * The test data root of this module, relative to the community home.
 * It is a constant, because an annotation argument must be a compile-time constant.
 */
internal const val COMPOSE_RESOURCES_TEST_DATA_RELATIVE_PATH: String =
  "plugins/compose/intellij.compose.ide.plugin.resources/testData"

/** The absolute test data root of this module. */
internal fun composeResourcesTestDataRoot(): Path =
  Path.of(PathManagerEx.getCommunityHomePath(), COMPOSE_RESOURCES_TEST_DATA_RELATIVE_PATH)
