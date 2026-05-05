// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertSingle
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.application
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.importing.GradleKotlinDslBaseScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncListener
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Companion.BASE_SCRIPT_MODEL_PHASE
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleTestApplication
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.util.concurrent.ConcurrentHashMap

@UseK2PluginMode
@GradleTestApplication
@AssertKotlinPluginMode
class KotlinGradleScriptImportingTest {

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

    @Test
    fun `test script entities from all linked builds should be preserved in workspace model`(): Unit = runBlocking {

        val project1Root = testRoot.resolve("project1")
        val project1SettingsFile = project1Root.createSettingsFile(gradleVersion) { setProjectName("project1") }
        val project1BuildFile = project1Root.createBuildFile(gradleVersion)

        val project2Root = testRoot.resolve("project2")
        val project2SettingsFile = project2Root.createSettingsFile(gradleVersion) { setProjectName("project2") }
        val project2BuildFile = project2Root.createBuildFile(gradleVersion)

        gradleFixture.openProject(project1Root).useProjectAsync { project ->
            gradleFixture.linkProject(project, project2Root)

            val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
            val expectedScripts = sequenceOf(project1SettingsFile, project1BuildFile, project2SettingsFile, project2BuildFile)
                .map { it.toVirtualFileUrl(virtualFileUrlManager) }
            val actualScripts = project.workspaceModel.currentSnapshot.entities<KotlinScriptEntity>()
                .map { it.virtualFileUrl }
            assertEqualsUnordered(expectedScripts.toList(), actualScripts.toList()) {
                "Incorrect list of KotlinScriptEntity generated during Gradle sync"
            }
        }
    }

    @Test
    fun `test base script model is applied to files not opened in editor`(): Unit = runBlocking {
        val projectRoot = testRoot.resolve("project").apply {
            createSettingsFile(gradleVersion) { setProjectName("project") }
            createBuildFile(gradleVersion)
        }

        val entitiesAtPhases = ConcurrentHashMap<GradleSyncPhase, Set<GradleSyncPhase>>()
        application.messageBus.connect(asDisposable())
            .subscribe(GradleSyncListener.TOPIC, object : GradleSyncListener {
                override fun onSyncPhaseCompleted(context: ProjectResolverContext, phase: GradleSyncPhase) {
                    entitiesAtPhases[phase] = context.project.workspaceModel.currentSnapshot
                        .entitiesBySource { it is GradleKotlinScriptEntitySource }
                        .map { it.entitySource }
                        .filterIsInstance<GradleKotlinScriptEntitySource>()
                        .map { it.phase }
                        .toSet()
                }
            })

        gradleFixture.openProject(projectRoot).useProjectAsync {
            val entitiesAtPhase = entitiesAtPhases[BASE_SCRIPT_MODEL_PHASE]
            assertNotNull(entitiesAtPhase) {
                "$BASE_SCRIPT_MODEL_PHASE should be completed"
            }
            assertSingle(BASE_SCRIPT_MODEL_PHASE, entitiesAtPhase) {
                "Expected script entities at $BASE_SCRIPT_MODEL_PHASE"
            }
        }
    }
}
