// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.openapi.application.ex.PathManagerEx
import java.nio.file.Path

internal const val TARGET_GRADLE_VERSION = "8.13"
internal const val COMMON_MAIN = "commonMain"
internal const val ANDROID_MAIN = "androidMain"
internal const val IOS_MAIN = "iosMain"

/**
 * The test data root of this module, relative to the community home.
 * It is a constant, because an annotation argument must be a compile-time constant.
 */
internal const val COMPOSE_RESOURCES_TEST_DATA_RELATIVE_PATH: String =
  "plugins/compose/intellij.compose.ide.plugin.resources/testData"

/**
 * The name of the test data project, and its directory name under the test data root.
 * It is a constant, because an annotation argument must be a compile-time constant.
 */
internal const val COMPOSE_RESOURCES_PROJECT_NAME: String = "ComposeResources"

/** The absolute test data root of this module. */
internal fun composeResourcesTestDataRoot(): Path =
  Path.of(PathManagerEx.getCommunityHomePath(), COMPOSE_RESOURCES_TEST_DATA_RELATIVE_PATH)

/** The absolute root of the test data project. */
internal fun composeResourcesProjectRoot(): Path =
  composeResourcesTestDataRoot().resolve(COMPOSE_RESOURCES_PROJECT_NAME)
