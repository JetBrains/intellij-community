// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.newTests

import org.jetbrains.kotlin.gradle.newTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.highlighting.HighlightingChecker
import org.jetbrains.kotlin.gradle.newTests.testFeatures.checkers.orderEntries.OrderEntriesChecker
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("newMppTests/regress")
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
    fun testKtij22345SyntheticJavaProperties() {
        doTest {
            onlyCheckers(HighlightingChecker)
            hideLineMarkers = true
        }
    }
}
