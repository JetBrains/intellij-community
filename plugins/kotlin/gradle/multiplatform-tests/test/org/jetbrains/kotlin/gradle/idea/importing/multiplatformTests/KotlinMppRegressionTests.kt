// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/regress")
class KotlinMppRegressionTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true
    }

    @Test
    fun testKT42381BadDependencyOnArtifactInsteadOfSources() {
        doTest {
            onlyModules(".*p1\\.jvm.*")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testKT42392BadDependencyOnForeignCommonTest() {
        doTest {
            onlyModules(""".*p1\.main.*""")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testKT46417NativePlatformTestSourceSets() {
        doTest {
            onlyModules(""".*p2\.(iosTest|iosX64Test|iosArm64Test|linuxX64Test|linuxArm64Test).*""")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testKtij20056lowerTransitiveStdlibInPlatformSourceSet() {
        doTest {
            onlyModules(""".*p.jvmMain.*""")
            hideStdlib = false
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.30-dev-1098+")
    fun testKtij22345SyntheticJavaProperties() {
        doTest {
            onlyCheckers(HighlightingChecker)
            hideLineMarkers = true
        }
    }

    @Test
    fun testKTIJ29619KotlinJvmWithTestFixtures() {
        doTest {
            onlyCheckers(HighlightingChecker)
            hideLineMarkers = true
        }
    }

    @Test
    fun testKTIJ7642UseIRSpecificFrontendChecker() {
        doTest {
            onlyCheckers(HighlightingChecker)
        }
    }

    /**
     * Test for
     * https://youtrack.jetbrains.com/issue/KTIJ-18375
     * https://youtrack.jetbrains.com/issue/KTIJ-20816
     *
     * ### Test Setup
     * This test has two subprojects:
     *  - producer: Regular kotlin/jvm library
     *     - publishes using jvmTarget 11
     *     - publishes into local repository into the project folder
     *     - defines an inline function 'inlineMe'
     *  - consumer: kotlin/multiplatform: Defining only a single jvm target
     *     - adds dependency to published 'producer' project (binary only!)
     *     - uses 'inlineMe' in src/commonMain/commonMain.kt
     *     - uses 'inlineMe' in src/jvmMain/jvmMain.kt
     *
     * ### Bad behavior (before fix)
     *  - consumer/commonMain would not properly set up its 'jvmTarget' (defaults to 1.8)
     *  - InlinePlatformCompatibilityChecker would fire (detecting that the binary using newer jvmTarget '11')
     *
     * ### Expected behavior
     * - Highlighting is fully green. Compiler does not complain, jvmTarget is set to 11 for commonMain as well as jvmMain
     */
    @Test
    @PluginTargetVersions(pluginVersion = "1.9.20-dev+")
    fun testKTIJ18375CommonSourceSetJvmTarget() {
        doTest {
            // Using jvmToolchain API to select the JDK
            allowAccessToDirsIfExists("/Library/Java/")
            publish("producer")
            onlyCheckers(HighlightingChecker, KotlinFacetSettingsChecker, GradleProjectsPublishingTestsFeature)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "2.1.20-dev-201+")
    fun testKTIJ30915KotlinNewKlibRefreshInVfs() {
        doTest(
            afterImport = { context ->
                val cinteropHeaderFile = context.testProjectRoot.resolve("libs/include/interop/myInterop.h")
                cinteropHeaderFile.writeText(cinteropHeaderFile.readText().replace("BAG", "BAT"))
                // The problem with VFS occures only with second import after changing .h file.
                // Also, see: KTIJ-30915
                importProject()
            }) {
            onlyCheckers(HighlightingChecker)
            hideLineMarkers = true
        }
    }
}
