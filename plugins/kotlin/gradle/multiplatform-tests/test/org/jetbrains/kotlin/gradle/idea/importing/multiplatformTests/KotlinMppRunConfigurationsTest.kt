// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.CustomChecksDsl
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.ExecuteRunConfigurationsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.RunConfigurationsChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/runConfigurations")
class KotlinMppRunConfigurationsTest : AbstractKotlinMppGradleImportingTest(), CustomChecksDsl {

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(RunConfigurationsChecker, ExecuteRunConfigurationsChecker)
        disableCheckers(HighlightingChecker)
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testJvmRun() {
        doTest {
            onlyModules(""".*(commonMain|jvmMain|iosMain)""")
            executeRunConfiguration("commonMain.main")
            executeRunConfiguration("jvmMain.main")
        }
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-dev-6845+")
    @Test
    fun testKmmTests() {
        /*
         KTIJ-24270:
         TestLauncher API from Gradle does not yet support Kotlin/Native's test tasks
         (which are based upon AbstractTestTask not on 'Test').

         Gradle will add support in Gradle 8.3
         Setting this registry manually will not be required after 'KTIJ-24270' workaround.
         */
        setRegistryPropertyForTest("gradle.testLauncherAPI.enabled", "false")

        doTest {
            executeRunConfiguration("CommonTest.test in commonTest - success")
            executeRunConfiguration("CommonTest.test in commonTest - failure")
            executeRunConfiguration("IosTest.test in iosTest - success")
            executeRunConfiguration("IosTest.test in iosTest - failure")
            executeRunConfiguration("JvmTest.test in jvmTest - success")
            executeRunConfiguration("JvmTest.test in jvmTest - failure")
        }
    }
}
