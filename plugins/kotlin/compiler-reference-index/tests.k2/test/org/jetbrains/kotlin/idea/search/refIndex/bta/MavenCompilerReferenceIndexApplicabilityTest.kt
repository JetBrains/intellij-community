// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.test.runTest
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenCompilerReferenceIndexApplicabilityTest(mavenVersion: String, modelVersion: String) :
    AbstractMavenCompilerReferenceIndexTest(mavenVersion, modelVersion) {

    @Test
    fun `test BTA CRI provider is not applicable when Maven CRI generation property is disabled`() = runTest {
        maven.importProjectAsync(mavenProjectWithCriValue("false"))

        assertFalse(isBtaCriProviderApplicable())
    }

    @Test
    fun `test BTA CRI provider is applicable when Maven CRI generation property is enabled`() = runTest {
        maven.importProjectAsync(mavenProjectWithCriValue("true"))

        assertTrue(isBtaCriProviderApplicable())
    }

    @Test
    fun `test BTA CRI provider is not applicable when Maven CRI generation property is absent`() = runTest {
        maven.importProjectAsync(mavenProjectWithoutCri())

        assertFalse(isBtaCriProviderApplicable())
    }

    private fun isBtaCriProviderApplicable(): Boolean =
        KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project).isBtaCriProvider()
}
