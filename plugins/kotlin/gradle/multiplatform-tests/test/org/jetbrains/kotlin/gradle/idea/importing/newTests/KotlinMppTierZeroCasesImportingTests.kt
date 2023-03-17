// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.idea.importing.newTests

import com.intellij.lang.annotation.HighlightSeverity
import org.jetbrains.kotlin.gradle.newTests.AbstractKotlinMppGradleImportingTest
import org.jetbrains.kotlin.gradle.newTests.TestConfigurationDslScope
import org.jetbrains.kotlin.test.TestMetadata
import org.junit.Test

@TestMetadata("newMppTests/tier0")
class KotlinMppTierZeroCasesImportingTests : AbstractKotlinMppGradleImportingTest() {
    override fun TestConfigurationDslScope.defaultTestConfiguration() {
        hideHighlightsBelow = HighlightSeverity.ERROR
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
