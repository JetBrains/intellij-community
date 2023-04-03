// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.facets.KotlinFacetSettingsChecker
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("multiplatform/core/experimentalTier")
class KotlinMppExperimentalTierCasesImportingTest : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideResourceRoots = true
        hideHighlightsBelow = HighlightSeverity.ERROR
    }

    @Test
    fun testCommonMainIsNativeShared() {
        doTest()
    }

    @Test
    fun testJvmAndAndroidBinary() {
        doTest {
            publish("producer")
        }
    }

    @Test
    fun testJvmAndAndroidSource() {
        doTest()
    }

    @Test
    fun testAndroidOnJvm() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*p1.*", to = ".*p2.*")
        }
    }

    @Test
    fun testAndroidOnJvmTransitive() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*p1.*", to = ".*p3.*")
        }
    }

    @Test
    fun testAndroidOnJvmIntransitive() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*p1.*", to = ".*p3.*")
        }
    }

    @Test
    fun testAndroidOnJvmAndAndroid() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*androidOnly.*", to = ".*jvmAndAndroid.*")
        }
    }

    @Test
    fun testAndroidOnJvmAndAndroidTransitive() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*androidOnly.*", to = ".*jvmAndAndroidTransitive.*")
        }
    }

    @Test
    fun testAndroidOnJvmAndAndroidIntransitive() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*androidOnly.*", to = ".*jvmAndAndroidTransitive.*")
        }
    }

    @Test
    fun testAndroidOnJvmAndAndroidIntermediateTransitive() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            onlyDependencies(from = ".*androidOnly.*", to = ".*jvmAndAndroidTransitive.*")
        }
    }


    @Test
    fun testJvmWithJava() {
        doTest {
            hideStdlib = true
            excludeModules(""".*ios.*""")
            onlyCheckers(OrderEntriesChecker)
        }
    }

    @Test
    fun testSingleAndroidTarget() {
        doTest()
    }

    @Test
    fun testDesktopTargets() {
        doTest()
    }

    @Test
    fun testHmppWithJvmAndAndroidSpecificDependencies() {
        doTest {
            onlyCheckers(OrderEntriesChecker)

            excludeDependencies(".*consumer.*")
            onlyModules(".*consumer.*")

            publish("expectedEverywhere", "expectedInJvmAndAndroid", "expectedInJvmOnly")
            hideStdlib = true
            hideKotlinTest = true
            hideKotlinNativeDistribution = true
        }
    }

    @Test
    fun testSimilarTargetsBamboo() {
        doTest {
            onlyCheckers(KotlinFacetSettingsChecker, OrderEntriesChecker)
            onlyFacetFields(KotlinFacetSettings::targetPlatform)
        }
    }
}
