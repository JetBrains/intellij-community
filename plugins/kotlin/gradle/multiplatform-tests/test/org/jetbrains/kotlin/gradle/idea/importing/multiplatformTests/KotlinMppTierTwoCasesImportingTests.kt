package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.GradleProjectsPublishingTestsFeature
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("multiplatform/core/tier2")
class KotlinMppTierTwoCasesImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR

        // checked in Tier0
        hideStdlib = true
        hideKotlinTest = true
        hideKotlinNativeDistribution = true
    }

    @Test
    fun testTransitiveMppBinaryBinary() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)
            publish("transitive", "direct")

            onlyDependencies(from = ".*consumer.*", to = ".*transitive.*")
            hideLineMarkers = true
        }
    }

    @Test
    fun testTransitiveMppSourceBinary() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)
            publish("transitive")

            onlyDependencies(from = ".*consumer.*", to = ".*transitive.*")
            hideLineMarkers = true
        }
    }

    @Test
    fun testTransitiveMppSourceSource() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*consumer.*", to = ".*transitive.*")
            hideLineMarkers = true
        }
    }

    @Test
    fun testAdvancedMppLibraryDependencyInIntermediateSource() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*consumer.*", to = ".*producer.*")
        }
    }

    @Test
    fun testAdvancedMppLibraryDependencyInIntermediateBinary() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)
            onlyDependencies(from = ".*consumer.*", to = ".*producer.*")

            publish("producer")
        }
    }

    @Test
    fun testAndroidConsumerTransitiveMppBinaryBinary() {
        doTest {
            onlyCheckers(OrderEntriesChecker, GradleProjectsPublishingTestsFeature)
            publish("transitive", "direct")

            onlyDependencies(from = ".*consumer.*", to = ".*transitive.*")
            hideLineMarkers = true
        }
    }

    @Test
    fun testAndroidConsumerTransitiveMppSourceSource() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*consumer.*", to = ".*transitive.*")
            hideLineMarkers = true
        }
    }
}
