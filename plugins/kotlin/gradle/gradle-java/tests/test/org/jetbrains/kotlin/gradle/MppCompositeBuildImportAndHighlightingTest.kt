// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.newTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.newTests.testServices.WorkspaceChecks
import org.jetbrains.kotlin.gradle.workspace.WorkspacePrintingMode
import org.jetbrains.kotlin.gradle.workspace.WorkspacePrintingMode.*
import org.jetbrains.kotlin.tooling.core.compareTo
import org.junit.Assume
import org.junit.Assume.assumeTrue
import org.junit.Test

class MppCompositeBuildImportTest : AbstractKotlinMppGradleImportingTest("gradle/newMppTests/compositeBuild") {

    @Test
    @WorkspaceChecks(MODULE_DEPENDENCIES, SOURCE_ROOTS)
    fun sample0() {
        // TODO: Run highlighting check as well
        // FIXME: jvmMain -> jvmMain ist not properly resolved, yet
        assumeTrue("Requires next bootstrap", kotlinTestPropertiesService.kotlinGradlePluginVersion > "1.8.20-dev-3308")
        doTest {
            linkProject("consumerBuild")
            hideResourceRoots = true
            hideStdlib = true
            hideKotlinTest = true
            hideKotlinNativeDistribution = true
        }
    }
}
