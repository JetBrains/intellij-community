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

    @Test
    fun `test BTA CRI provider is applicable by default since Kotlin 2_5 with incremental compilation`() = runTest {
        maven.importProjectAsync(mavenProjectWithKotlinPlugin(kotlinVersion = "2.5.0", incremental = "true"))

        assertTrue(isBtaCriProviderApplicable())
    }

    @Test
    fun `test BTA CRI provider is not applicable by default since Kotlin 2_5 without incremental compilation`() = runTest {
        maven.importProjectAsync(mavenProjectWithKotlinPlugin(kotlinVersion = "2.5.0", incremental = "false"))

        assertFalse(isBtaCriProviderApplicable())
    }

    private fun isBtaCriProviderApplicable(): Boolean =
        KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project).isBtaCriProvider()

    private fun mavenProjectWithKotlinPlugin(kotlinVersion: String, incremental: String): String =
        $$"""
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1.0.0</version>
        <properties>
            <kotlin.version>$$kotlinVersion</kotlin.version>
            <kotlin.compiler.incremental>$$incremental</kotlin.compiler.incremental>
        </properties>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.jetbrains.kotlin</groupId>
                    <artifactId>kotlin-maven-plugin</artifactId>
                    <version>${kotlin.version}</version>
                </plugin>
            </plugins>
        </build>
        """.trimIndent()
}
