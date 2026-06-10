// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.junit.Test

class MavenCompilerReferenceIndexApplicabilityTest : AbstractMavenCompilerReferenceIndexTest() {

    @Test
    fun `test BTA CRI provider is not applicable when Maven CRI generation property is disabled`() = runTest {
        importProjectAsync(mavenProjectWithCriValue("false"))

        assertFalse(isBtaCriProviderApplicable())
    }

    @Test
    fun `test BTA CRI provider is applicable when Maven CRI generation property is enabled`() = runTest {
        importProjectAsync(mavenProjectWithCriValue("true"))

        assertTrue(isBtaCriProviderApplicable())
    }

    @Test
    fun `test BTA CRI provider is not applicable when Maven CRI generation property is absent`() = runTest {
        importProjectAsync(mavenProjectWithoutCri())

        assertFalse(isBtaCriProviderApplicable())
    }

    private fun isBtaCriProviderApplicable(): Boolean =
        KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project).isBtaCriProvider()
}
