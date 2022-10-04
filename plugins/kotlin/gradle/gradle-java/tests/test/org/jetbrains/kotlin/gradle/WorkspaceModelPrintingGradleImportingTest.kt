// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle

import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class WorkspaceModelPrintingGradleImportingTest : AbstractWorkspaceModelPrintingGradleImportingTest() {
    @Test
    @PluginTargetVersions(pluginVersion = "1.6.0+")
    fun testSimpleProjectToProject() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.0+")
    fun testSimpleTwoLevel() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.6.0+")
    fun testNativeDistributionCommonization() {
        doTest()
    }
}