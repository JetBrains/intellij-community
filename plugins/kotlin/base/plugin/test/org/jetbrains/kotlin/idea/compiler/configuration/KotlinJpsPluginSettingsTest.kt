// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion.Companion.get
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings.Companion.shouldImportKotlinJpsPluginVersionFromExternalBuildSystem

class KotlinJpsPluginSettingsTest : UsefulTestCase() {
    private lateinit var myFixture: IdeaProjectTestFixture

    fun `test shouldImportKotlinJpsPluginVersionFromExternalBuildSystem`() {
        try {
            shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.6.0"))
            fail(
                "shouldImportKotlinJpsPluginVersionFromExternalBuildSystem should fail when the version is lower than " +
                        "${KotlinJpsPluginSettings.jpsMinimumSupportedVersion}"
            )
        } catch (ignored: IllegalArgumentException) {
        }
        assertEquals(false, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.0-Beta-1234")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.0")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.10-Beta-1234")))
        assertEquals(true, shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(get("1.7.10")))
    }

    fun `test multiple project import with different build systems`() {
        withRegistryKey("kotlin.jps.cache.external.system.id") {
            withProjectFixture {
                // Initial JPS setup
                importKotlinJpsVersionAndCheck("1.8.0", "", "1.8.0")

                // Switch to the external build system
                importKotlinJpsVersionAndCheck("1.7.21", "Maven", "1.7.21")

                // Upgrade and downgrade in one build system
                importKotlinJpsVersionAndCheck("1.8.0", "Maven", "1.8.0")
                importKotlinJpsVersionAndCheck("1.7.21", "Maven", "1.7.21")

                // Switch to another build system with the version upgrade
                importKotlinJpsVersionAndCheck("1.8.0", "Gradle", "1.8.0")

                // Same version, but another build system
                importKotlinJpsVersionAndCheck("1.8.0", "Bazel", "1.8.0", "Gradle")

                // Switch to another build system with the version downgrade
                importKotlinJpsVersionAndCheck("1.7.21", "Maven", "1.8.0", "Gradle")
            }
        }
    }

    private fun withProjectFixture(block: Project.() -> Unit) {
        val projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name)
        myFixture = projectBuilder.fixture
        myFixture.setUp()

        myFixture.project.block()
        try {
            myFixture.tearDown()
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    private fun Project.importKotlinJpsVersionAndCheck(
        versionToSetup: String,
        buildSystem: String,
        expectedVersion: String,
        expectedBuildSystem: String = buildSystem,
        isDelegated: Boolean = false,
    ) {
        val kotlinJpsPluginSettings = KotlinJpsPluginSettings.getInstance(this)
        val currentVersion = kotlinJpsPluginSettings.settings.version
        val currentBuildSystem = kotlinJpsPluginSettings.settings.externalSystemId

        KotlinJpsPluginSettings.importKotlinJpsVersionFromExternalBuildSystem(this, versionToSetup, isDelegated, buildSystem)

        val actualVersion = kotlinJpsPluginSettings.settings.version
        val actualBuildSystem = kotlinJpsPluginSettings.settings.externalSystemId

        assertEquals(
            "Try to setup $versionToSetup from $buildSystem to $currentVersion from ${currentBuildSystem.ifEmpty { "<no build system provided>" }}.\n" +
                    "Expected: $expectedVersion from $buildSystem, but got: $actualVersion from ${actualBuildSystem.ifEmpty { "<no build system provided>" }}",
            expectedVersion,
            actualVersion
        )
        assertEquals(
            "Try to setup Kotlin JPS version from ${buildSystem.ifEmpty { "<no build system provided>" }}. But $actualBuildSystem was set",
            expectedBuildSystem,
            actualBuildSystem
        )
    }

    private fun withRegistryKey(key: String, block: () -> Unit) {
        Registry.get(key).setValue(true)
        block()
    }
}
