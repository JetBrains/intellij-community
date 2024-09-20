// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.config.IKotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.AggregatedExternalLibrariesChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.contentRoots.ContentRootsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

@TestMetadata("multiplatform/core/features/misc")
class KotlinMppMiscCasesImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true

        onlyFacetFields(IKotlinFacetSettings::targetPlatform)

        hideResourceRoots = true
    }

    @Test
    fun testDiamond() {
        doTest {
            onlyCheckers(OrderEntriesChecker, KotlinFacetSettingsChecker, ContentRootsChecker)
        }
    }

    @Test
    fun testOrphanSourceSet() {
        doTest {
            onlyCheckers(KotlinFacetSettingsChecker)
        }
    }

    @Test
    fun testDependencyOnStdlibFromPlatformSourceSets() {
        doTest {
            hideStdlib = false
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testDependencyOnKotlinTestFromPlatformSourceSets() {
        doTest {
            hideKotlinTest = false
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testMppLibAndHmppConsumer() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)

            publish("lib")
            excludeDependencies(""".*consumer.*""")
            excludeModules(""".*lib.*""")
        }
    }

    @Test
    fun testHmppLibAndMppConsumer() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)

            publish("lib")
            excludeDependencies(""".*consumer.*""")
            excludeModules(""".*lib.*""")
        }
    }

    @Test
    fun testUnresolvedDependency() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testBinaryDependenciesOrderIsStable() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)

            publish("lib1")
            publish("lib2")
            excludeDependencies(".*consumer.*")
            excludeModules(".*lib.*")
            sortDependencies = false
        }
    }

    @Test
    fun testSourceDependenciesOrderIsStable() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            excludeDependencies(".*consumer.*")
            excludeModules(".*lib.*")
            sortDependencies = false
        }
    }

    @Test
    fun testMismatchedAttributesDependencyBinary() {
        // NB: Variant-mismatch error is printed verbatim in stderr
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)

            /* Code Highlighting requires 1.9, because of native opt-in annotation in source files */
            if (kotlinPluginVersion < KotlinToolingVersion("1.9.20-dev-6845")) {
                disableCheckers(HighlightingChecker)
            }

            publish("producer")
            excludeDependencies(".*consumer.*")
            excludeModules(".*producer.*")

            hideHighlightsBelow = HighlightSeverity.ERROR
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.9.0-dev+") // resolution works differently in 1.8.0 and highlighting errors is not equal
    fun testMismatchedAttributesDependencySource() {
        // NB: Variant-mismatch error is printed verbatim in stderr
        doTest {
            onlyCheckers(OrderEntriesChecker)

            excludeDependencies(".*consumer.*")
            excludeModules(".*producer.*")

            hideHighlightsBelow = HighlightSeverity.ERROR
        }
    }

    @Test
    fun testKmmAppWithIntermediateExternalDependencies() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            // not concerned with inter-project dependencies
            excludeDependencies(".*rootProject.*")

            hideLineMarkers = true // too much noise
        }
    }

    @PluginTargetVersions(pluginVersion = "1.9.20-Beta+") // -Xexpect-actual-classes cannot be easily passed before
    @Test
    fun testTransitiveKmmLibraryThroughJava() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*m3-kt.*", to = ".*m1-kt-mpp.*")
            hideLineMarkers = true
        }
    }

    @Test
    fun testNativeUnsupportedPlatform() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            allowFilesNotUnderContentRoot = true
            hideStdlib = false
            hideKotlinNativeDistribution = false
            excludeModules(".*(jvmMain|jvmTest).*") // jvm-target is just to make commonMain not common-shared
        }
    }

    // This test checks only stable and released part of associate
    // configurations, i.e. the support for custom associated compilations
    // in Kotlin/JVM + support for default associated compilations in MPP
    @Test
    fun testAssociateCompilationIntegrationTest() {
        doTest {
            onlyCheckers(HighlightingChecker, KotlinFacetSettingsChecker)
            onlyFacetFields(IKotlinFacetSettings::additionalVisibleModuleNames)
            hideLineMarkers = true
        }
    }

    @Test
    @TestMetadata("projectDependenciesToMppProjectWithAdditionalCompilations")
    fun testProjectDependenciesToMppProjectWithAdditionalCompilations() {
        doTest {
            onlyCheckers(OrderEntriesChecker)
            onlyDependencies(from = ".*client.*", to = ".*libMpp.*")
        }
    }

    // This test ensures that a single library,
    // on which both the MPP module and the Java-only module depend,
    // imports with the same name,
    // and it is not duplicated with different names in "External libraries tree"
    @Test
    fun testNoLibraryDuplicationTest() {
        doTest {
            onlyCheckers(AggregatedExternalLibrariesChecker)
        }
    }
}
