// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

abstract class GradleQuickFixTest : AbstractGradleMultiFileQuickFixTest() {
    class CreateActualForJs : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForJs() = doMultiFileQuickFixTest()
    }

    class CreateActualForJsTest : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForJsTest() = doMultiFileQuickFixTest()
    }

    class CreateActualForJvm : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForJvm() = doMultiFileQuickFixTest()
    }

    class CreateActualForJvmTest : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForJvmTest() = doMultiFileQuickFixTest()
    }

    class CreateActualForJvmTestWithCustomPath : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForJvmTestWithCustomPath() = doMultiFileQuickFixTest()
    }

    class CreateActualForJvmTestWithCustomExistentPath : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForJvmTestWithCustomExistentPath() = doMultiFileQuickFixTest()
    }

    class CreateActualForNativeIOS : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForNativeIOS() = doMultiFileQuickFixTest()
    }

    class CreateActualForNativeIOSWithExistentPath : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForNativeIOSWithExistentPath() = doMultiFileQuickFixTest()
    }

    class CreateActualForGranularSourceSetTarget : GradleQuickFixTest() {
        @Test
        @PluginTargetVersions(pluginVersion = "1.5.31+")
        fun testCreateActualForGranularSourceSetTarget() = doMultiFileQuickFixTest()
    }
}
