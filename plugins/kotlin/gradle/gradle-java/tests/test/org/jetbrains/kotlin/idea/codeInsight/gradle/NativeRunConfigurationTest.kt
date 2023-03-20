// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.execution.PsiLocation
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import org.jetbrains.kotlin.gradle.GradleDaemonAnalyzerTestCase
import org.jetbrains.kotlin.gradle.newTests.OldMppTestsInfraDuplicate
import org.jetbrains.kotlin.idea.test.TagsTestDataUtil
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

@OldMppTestsInfraDuplicate
abstract class NativeRunConfigurationTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName(): String = "nativeRunConfiguration"

    class MultiplatformNativeRunGutter : NativeRunConfigurationTest() {
        @Test
        @TargetVersions("6.0+")
        fun multiplatformNativeRunGutter() {
            doTest()
        }
    }

    class MultiplatformWithoutHmppNativeRunGutter : NativeRunConfigurationTest() {
        @Test
        @TargetVersions("6.0+")
        fun multiplatformWithoutHmppNativeRunGutter() {
            doTest()
        }
    }

    class CustomEntryPointWithoutRunGutter : NativeRunConfigurationTest() {
        @Test
        @TargetVersions("6.0+")
        fun multiplatformWithoutHmppNativeRunGutter() {
            doTest()
        }

        @Test
        @TargetVersions("6.0+")
        fun customEntryPointWithoutRunGutter() {
            doTest()
        }
    }

    protected fun doTest() {
        val files = importProjectFromTestData()
        val project = myTestFixture.project

        org.jetbrains.kotlin.gradle.checkFiles(
            files.filter { it.extension == "kt" },
            project,
            object : GradleDaemonAnalyzerTestCase(
                testLineMarkers = true,
                checkWarnings = false,
                checkInfos = false,
                rootDisposable = testRootDisposable
            ) {
                override fun renderAdditionalAttributeForTag(tag: TagsTestDataUtil.TagInfo<*>): String? {
                    val lineMarkerInfo = tag.data as? LineMarkerInfo<*> ?: return null
                    val gradleRunConfigs = lineMarkerInfo.extractConfigurations().filter { it.configuration is GradleRunConfiguration }
                    val runConfig = gradleRunConfigs.singleOrNull() // can we have more than one?

                    val settings = (runConfig?.configurationSettings?.configuration as? GradleRunConfiguration)?.settings ?: return null

                    return "settings=\"${settings}\""
                }

            }
        )
    }

    private fun LineMarkerInfo<*>.extractConfigurations(): List<ConfigurationFromContext> {
        val location = PsiLocation(element)
        val emptyContext = ConfigurationContext.createEmptyContextForLocation(location)
        return emptyContext.configurationsFromContext.orEmpty()
    }
}
