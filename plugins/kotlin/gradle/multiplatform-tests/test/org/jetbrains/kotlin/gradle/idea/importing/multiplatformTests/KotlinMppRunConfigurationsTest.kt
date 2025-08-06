// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.findFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.CustomGradlePropertiesTestFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.hooks.KotlinMppTestHooks
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.ExecuteRunConfigurationsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.runConfigurations.RunConfigurationsChecker
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.tooling.core.compareTo
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import javax.swing.Icon

@TestMetadata("multiplatform/core/features/runConfigurations")
class KotlinMppRunConfigurationsTest : AbstractKotlinMppGradleImportingTest() {

    override val pluginMode: KotlinPluginMode
        get() = if (kotlinPluginVersion >= "2.0.20-dev-0") KotlinPluginMode.K2
        else KotlinPluginMode.K1

    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        onlyCheckers(RunConfigurationsChecker, ExecuteRunConfigurationsChecker, CustomGradlePropertiesTestFeature, KotlinMppTestHooks)
        disableCheckers(HighlightingChecker)
        /* When executing tests, we do not care about deprecation warnings in our build output */
        addCustomGradleProperty("org.gradle.warning.mode", "none")
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
            onlyModules("project.*")
            executeRunConfiguration("CommonTest.test in commonTest - success")
            executeRunConfiguration("CommonTest.test in commonTest - failure")
            executeRunConfiguration("IosTest.test in iosTest - success")
            executeRunConfiguration("IosTest.test in iosTest - failure")
            executeRunConfiguration("JvmTest.test in jvmTest - success")
            executeRunConfiguration("JvmTest.test in jvmTest - failure")
        }
    }

    @PluginTargetVersions(pluginVersion = "2.0.20-dev-0+")
    @Test
    fun testNativeTest() {
        doTest {
            onlyModules(".*nativeTest")
            executeRunConfiguration("foo.bar.NativeTest")
            /**
             * Let's test the line-markers after the test executed
             *
             * ```kotlin
             * class NativeTest { // <- RED!
             *     @Test
             *     fun failure() // <- RED!
             *
             *     @Test
             *     fun success() // <- GREEN!
             * }
             * ```
             */
            runAfterTestExecution {
                val nativeTestFile = projectRoot.findFile("src/nativeTest/kotlin/NativeTest.kt") ?: error("Missing 'NativeTest.kt'")
                runInEdtAndWait {
                    codeInsightTestFixture.openFileInEditor(nativeTestFile)
                    codeInsightTestFixture.doHighlighting()
                    val psi = PsiManager.getInstance(myProject).findFile(nativeTestFile) ?: error("Missing 'NativeTest.kt' PsiFile")
                    val document = PsiDocumentManager.getInstance(myProject).getDocument(psi) ?: error("Missing 'NativeTest.kt' Document")
                    val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, myProject)

                    fun assertStateAtText(text: String, icon: Icon) {
                        val lineMarker = lineMarkers.find { marker -> marker.element?.text == text }
                            ?: error("Missing line marker for '$text'")

                        kotlin.test.assertEquals(icon, lineMarker.icon, "Wrong line-marker at '$text'")
                    }

                    assertStateAtText("NativeTest", AllIcons.RunConfigurations.TestState.Red2)
                    assertStateAtText("success", AllIcons.RunConfigurations.TestState.Green2)
                    assertStateAtText("failure", AllIcons.RunConfigurations.TestState.Red2)
                }
            }
        }
    }


    @PluginTargetVersions(pluginVersion = "2.0.20-dev-0+")
    @Test
    fun testCommonTest() {
        doTest {
            onlyModules(".*commonTest")
            executeRunConfiguration("foo.bar.CommonTest")
            /**
             * Let's test the line-markers after the test executed
             *
             * ```kotlin
             * class CommonTest { // <- RED!
             *     @Test
             *     fun failure() // <- RED!
             *
             *     @Test
             *     fun success() // <- GREEN!
             * }
             * ```
             */
            runAfterTestExecution {
                val nativeTestFile = projectRoot.findFile("src/commonTest/kotlin/CommonTest.kt") ?: error("Missing 'CommonTest.kt'")
                runInEdtAndWait {
                    codeInsightTestFixture.openFileInEditor(nativeTestFile)
                    codeInsightTestFixture.doHighlighting()
                    val psi = PsiManager.getInstance(myProject).findFile(nativeTestFile) ?: error("Missing 'CommonTest.kt' PsiFile")
                    val document = PsiDocumentManager.getInstance(myProject).getDocument(psi) ?: error("Missing 'CommonTest.kt' Document")
                    val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, myProject)

                    fun assertStateAtText(text: String, icon: Icon) {
                        val lineMarker = lineMarkers.find { marker -> marker.element?.text == text }
                            ?: error("Missing line marker for '$text'")

                        kotlin.test.assertEquals(icon, lineMarker.icon, "Wrong line-marker at '$text'")
                    }

                    assertStateAtText("CommonTest", AllIcons.RunConfigurations.TestState.Red2)
                    assertStateAtText("success", AllIcons.RunConfigurations.TestState.Green2)
                    assertStateAtText("failure", AllIcons.RunConfigurations.TestState.Red2)
                }
            }
        }
    }
}
