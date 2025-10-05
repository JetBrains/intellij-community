// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.DEFAULT_SYNC_TIMEOUT
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.observable.operation.core.awaitOperation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.testFramework.DumbModeTestUtils.startEternalDumbModeTask
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.withProjectAsync
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.jetbrains.plugins.gradle.util.getGradleProjectReloadOperation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

@UseK2PluginMode
@TestApplication
@AssertKotlinPluginMode
class GradleScriptingTest {
    val gradleVersion: GradleVersion = GradleVersion.current()
    val javaVersion = JavaVersionRestriction.NO

    val testDisposable by disposableFixture()
    val gradleFixture by gradleFixture()
    val gradleJvmFixture by gradleJvmFixture(gradleVersion, javaVersion)
    val testRoot by tempPathFixture()

    @BeforeEach
    fun setUpTests() {
        gradleJvmFixture.installProjectSettingsConfigurator(testDisposable)
        ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
    }

    //KTIJ-34260
    @SystemProperty("intellij.progress.task.ignoreHeadless", "true")
    @Test
    fun processingKotlinScriptShouldNotBlockGradleSync(): Unit = runBlocking {
        val projectRoot = testRoot.resolve("project")
        projectRoot.createSettingsFile(gradleVersion) {
            setProjectName("project")
        }

        gradleFixture.openProject(projectRoot).withProjectAsync { project ->
            val reloadOperation = getGradleProjectReloadOperation(project, asDisposable())

            gradleFixture.awaitProjectConfiguration(project) {
                dumbMode(project) {
                    reloadOperation.awaitOperation(10.seconds, DEFAULT_SYNC_TIMEOUT) {
                        launchReloadProject(project, projectRoot)
                    }
                }
            }
        }.useProjectAsync { project ->
            ModuleAssertions.assertModules(project, "project")
        }
    }

    fun launchReloadProject(project: Project, projectPath: Path) {
        ExternalSystemUtil.refreshProject(
            projectPath.toCanonicalPath(),
            ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
        )
    }

    private inline fun <T> dumbMode(project: Project, computable: () -> T): T {
        startEternalDumbModeTask(project).use {
            return computable.invoke()
        }
    }
}