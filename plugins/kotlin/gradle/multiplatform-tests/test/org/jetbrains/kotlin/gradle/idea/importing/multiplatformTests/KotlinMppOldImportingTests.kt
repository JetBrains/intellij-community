// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.enableKgpDependencyResolutionParam
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/oldImport/tier0")
class KotlinMppTierZeroCasesOldImportingTests: AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR
        addCustomGradleProperty(enableKgpDependencyResolutionParam, "false")
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKmmApplication() {
        doTest()
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKmmLibrary() {
        doTest()
    }
}


@TestMetadata("multiplatform/core/oldImport/tier1")
class KotlinMppTierOneCasesOldImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR

        // checked in Tier0 for most cases
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true
        addCustomGradleProperty(enableKgpDependencyResolutionParam, "false")
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testTwoKmmLibrariesSource() {
        doTest {
            // Inter-project dependencies are checked in Tier Zero, only concerned
            // with dependencies on kmmLib
            excludeDependencies(".*kmmConsumer.*")

            // Don't list modules from kmmLib, we only interested on dependencies on them
            excludeModules(".*kmmLib.*")

            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testTwoKmmLibrariesBinary() {
        doTest {
            // Inter-project dependencies are checked in Tier Zero, only concerned
            // with dependencies on kmmLib
            excludeDependencies(".*kmmConsumer.*")

            // Don't list modules from kmmLib, we only interested on dependencies on them
            excludeModules(".*kmmLib.*")

            publish("kmmLib")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testAndroidAppConsumesKmmLibSource() {
        doTest {
            // Inter-project dependencies are checked in Tier Zero, only concerned
            // with dependencies on kmmLib
            excludeDependencies(".*androidApp.*")

            // Don't list modules from kmmLib, we only interested on dependencies on them
            excludeModules(".*kmmLib.*")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testAndroidAppConsumesKmmLibBinary() {
        doTest {
            // Inter-project dependencies are checked in Tier Zero, only concerned
            // with dependencies on kmmLib
            excludeDependencies(".*androidApp.*")

            // Don't list modules from kmmLib, we only interested on dependencies on them
            excludeModules(".*kmmLib.*")

            publish("kmmLib")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKmmAppConsumesAndroidLibSource() {
        doTest {
            excludeModules(".*(androidLib|common|ios).*") // <=> only Android-platform modules of kmmApp
            excludeDependencies(".*kmmApp.*") // don't show inter-project dependencies
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKmmAppConsumesAndroidLibBinary() {
        doTest {
            excludeModules(".*(androidLib|common|ios).*") // <=> only Android-platform modules of kmmApp
            excludeDependencies(".*kmmApp.*") // don't show inter-project dependencies

            publish("androidLib")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKotlinJvmConsumesKmmLibBinary() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            onlyDependencies(from = ".*kJvm.*", to = ".*kmmLib.*")
            publish("kmmLib")
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testKotlinJvmConsumesKmmLibSource() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            onlyDependencies(from = ".*kJvm.*", to = ".*kmmLib.*")
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testPureJavaConsumesKmmLibBinary() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            onlyDependencies(from = ".*javaOnly.*", to = ".*kmmLib.*")
            publish("kmmLib")
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.8.20+")
    fun testAdvancedMppLibrary() {
        doTest {
            // doesn't introduce any conceptually new source sets (like JVM+Android shared ones)
            disableCheckers(ContentRootsChecker)

            hideStdlib = false
            hideKotlinTest = false
            hideKotlinNativeDistribution = false
        }
    }
}