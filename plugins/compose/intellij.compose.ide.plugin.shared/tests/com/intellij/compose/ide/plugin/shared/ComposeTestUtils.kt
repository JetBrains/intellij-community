// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared

import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.plugins.gradle.testFramework.fixtures.FileTestFixture

const val COMMON_MAIN = "commonMain"
const val DESKTOP_MAIN = "desktopMain"
const val WASM_JS_MAIN = "wasmJsMain"
const val ANDROID_MAIN = "androidMain"
const val IOS_MAIN = "iosMain"

const val TARGET_GRADLE_VERSION = "8.14.4"

/**
 * Writes the Android SDK path into `local.properties` of the test data project.
 * The Android Gradle plugin and the IDE SDK sync read the SDK path from there. Without it, both need `ANDROID_HOME`.
 * A test of a project with an Android target applies it in [ComposeBaseTestCase.configureProject].
 */
fun FileTestFixture.Builder.withAndroidSdk() {
  withFile("local.properties", "sdk.dir=${KotlinTestUtils.getAndroidSdkSystemIndependentPath()}")
}
