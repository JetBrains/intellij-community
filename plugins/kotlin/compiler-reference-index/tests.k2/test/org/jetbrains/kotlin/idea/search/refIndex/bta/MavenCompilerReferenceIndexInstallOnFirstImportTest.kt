// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.search.refIndex.bta

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenCompilerReferenceIndexInstallOnFirstImportTest(mavenVersion: String, modelVersion: String) :
    AbstractMavenCompilerReferenceIndexTest(mavenVersion, modelVersion) {
    @Test
    fun `test BTA file watcher is installed after Maven import when CRI generation property is enabled`() = runTest {
        val service = preloadCriService()
        assertFalse(service.isBtaFileWatcherInstalled)

        maven.importProjectAsync(mavenProjectWithCriValue())

        assertTrue(service.isBtaFileWatcherInstalled)
    }

    @Test
    fun `test BTA file watcher is not installed after Maven import when CRI generation property is absent`() = runTest {
        val service = preloadCriService()
        assertFalse(service.isBtaFileWatcherInstalled)

        maven.importProjectAsync(mavenProjectWithoutCri())

        assertFalse(service.isBtaFileWatcherInstalled)
    }

    @Test
    fun `test BTA file watcher is not installed after Maven import when CRI generation property is disabled`() = runTest {
        val service = preloadCriService()
        assertFalse(service.isBtaFileWatcherInstalled)

        maven.importProjectAsync(mavenProjectWithCriValue("false"))

        assertFalse(service.isBtaFileWatcherInstalled)
    }
}
