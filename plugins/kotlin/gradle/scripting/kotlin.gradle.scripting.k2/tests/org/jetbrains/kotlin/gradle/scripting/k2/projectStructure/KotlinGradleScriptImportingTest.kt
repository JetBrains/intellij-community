// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertSingle
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.useProjectAsync
import com.intellij.util.asDisposable
import kotlinx.coroutines.runBlocking
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.workspaceModel.GradleKotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.plugins.gradle.importing.syncAction.whenSyncPhaseCompleted
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Companion.BASE_SCRIPT_MODEL_PHASE
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Companion.SCRIPT_MODEL_PHASE
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedClass
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@UseK2PluginMode
@TestApplication
@AssertKotlinPluginMode
@ParameterizedClass
@BaseGradleVersionSource
class KotlinGradleScriptImportingTest(private val gradleVersion: GradleVersion) {

    private val gradle by gradleFixture(gradleVersion)
    private val testRoot by tempPathFixture()

    @Test
    fun `test script entities from all linked builds should be preserved in workspace model`(): Unit = runBlocking {

        val project1Root = testRoot.resolve("project1")
        val project1SettingsFile = project1Root.createSettingsFile(gradleVersion) { setProjectName("project1") }
        val project1BuildFile = project1Root.createBuildFile(gradleVersion)

        val project2Root = testRoot.resolve("project2")
        val project2SettingsFile = project2Root.createSettingsFile(gradleVersion) { setProjectName("project2") }
        val project2BuildFile = project2Root.createBuildFile(gradleVersion)

        gradle.openProject(project1Root).useProjectAsync { project ->
            gradle.linkProject(project, project2Root)

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

        val entitiesAtPhases = collectScriptEntityPhasesAtSyncPhases(asDisposable())

        gradle.openProject(projectRoot).useProjectAsync {
            val entitiesAtPhase = entitiesAtPhases[BASE_SCRIPT_MODEL_PHASE]
            assertNotNull(entitiesAtPhase) {
                "$BASE_SCRIPT_MODEL_PHASE should be completed"
            }
            assertSingle(BASE_SCRIPT_MODEL_PHASE, entitiesAtPhase) {
                "Expected script entities at $BASE_SCRIPT_MODEL_PHASE"
            }
        }
    }

    @Test
    fun `test script entities visibility at sync phases`(): Unit = runBlocking {
        val projectRoot = testRoot.resolve("project").apply {
            createSettingsFile(gradleVersion) { setProjectName("project") }
            createBuildFile(gradleVersion)
        }

        val entitiesAtPhases = collectScriptEntityPhasesAtSyncPhases(asDisposable())

        fun assertEntities(expectedEntityAtPhase: GradleSyncPhase?, filter: Predicate<GradleSyncPhase>) {
            for ((phase, actualEntitiesAtPhase) in entitiesAtPhases.entries) {
                if (filter.test(phase)) {
                    assertNotNull(actualEntitiesAtPhase) {
                        "At least one phase should be completed $filter"
                    }
                    when (expectedEntityAtPhase) {
                        null -> assertEmpty(actualEntitiesAtPhase) {
                            "Unexpected script entities at $phase ($filter)"
                        }
                        else -> assertSingle(expectedEntityAtPhase, actualEntitiesAtPhase) {
                            "Expected script entities at $phase ($filter)"
                        }
                    }
                }
            }
        }

        gradle.openProject(projectRoot).useProjectAsync { project ->

            assertEntities(null, object : Predicate<GradleSyncPhase> {
                override fun toString(): String = "during initial sync before $BASE_SCRIPT_MODEL_PHASE"
                override fun test(phase: GradleSyncPhase): Boolean = phase < BASE_SCRIPT_MODEL_PHASE
            })
            assertEntities(BASE_SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                override fun toString(): String = "during initial sync between $BASE_SCRIPT_MODEL_PHASE and $SCRIPT_MODEL_PHASE"
                override fun test(phase: GradleSyncPhase): Boolean = phase in BASE_SCRIPT_MODEL_PHASE..<SCRIPT_MODEL_PHASE
            })
            assertEntities(SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                override fun toString(): String = "during initial sync after $SCRIPT_MODEL_PHASE"
                override fun test(phase: GradleSyncPhase): Boolean = phase >= SCRIPT_MODEL_PHASE
            })

            entitiesAtPhases.clear()
            gradle.syncProject(project, projectRoot)

            assertEntities(SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                override fun toString(): String = "during re-sync before $BASE_SCRIPT_MODEL_PHASE"
                override fun test(phase: GradleSyncPhase): Boolean = phase < BASE_SCRIPT_MODEL_PHASE
            })
            assertEntities(BASE_SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                override fun toString(): String = "during re-sync between $BASE_SCRIPT_MODEL_PHASE and $SCRIPT_MODEL_PHASE"
                override fun test(phase: GradleSyncPhase): Boolean = phase in BASE_SCRIPT_MODEL_PHASE..<SCRIPT_MODEL_PHASE
            })
            assertEntities(SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                override fun toString(): String = "during re-sync after $SCRIPT_MODEL_PHASE"
                override fun test(phase: GradleSyncPhase): Boolean = phase >= SCRIPT_MODEL_PHASE
            })
        }
    }

    private fun collectScriptEntityPhasesAtSyncPhases(parentDisposable: Disposable): MutableMap<GradleSyncPhase, Set<GradleSyncPhase>> {
        val entitiesAtPhases = ConcurrentHashMap<GradleSyncPhase, Set<GradleSyncPhase>>()
        whenSyncPhaseCompleted(parentDisposable) { context, phase ->
            entitiesAtPhases[phase] = context.project.workspaceModel.currentSnapshot
                .entitiesBySource { it is GradleKotlinScriptEntitySource }
                .map { it.entitySource }
                .filterIsInstance<GradleKotlinScriptEntitySource>()
                .map { it.phase }
                .toSet()
        }
        return entitiesAtPhases
    }
}
