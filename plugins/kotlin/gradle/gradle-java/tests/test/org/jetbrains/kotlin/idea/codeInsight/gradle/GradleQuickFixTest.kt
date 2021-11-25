// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class GradleQuickFixTest : AbstractGradleMultiFileQuickFixTest() {
    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJs() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJsTest() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvm() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTest() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTestWithCustomPath() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTestWithCustomExistentPath() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOS() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOSWithExistentPath() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForGranularSourceSetTarget() = doMultiFileQuickFixTest()
}
