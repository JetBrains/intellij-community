package org.jetbrains.kotlin.gradle.idea.importing.multiplatformTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.multiplatformTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.multiplatformTests.TestConfigurationDslScope
import org.jetbrains.kotlin.gradle.multiplatformTests.testFeatures.enableKgpDependencyResolutionParam
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("multiplatform/core/oldImport/tier0")
class KotlinMppTierZeroCasesOldImportingTests: AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR
        addCustomGradleProperty(enableKgpDependencyResolutionParam, "false")
    }

    @Test
    fun testKmmApplication() {
        doTest()
    }

    @Test
    fun testKmmLibrary() {
        doTest()
    }
}