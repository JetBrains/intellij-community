// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.projectStructure

import com.intellij.gradle.toolingExtension.util.GradleVersionSpecificsUtil
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
import org.jetbrains.plugins.gradle.importing.syncAction.whenSyncPhaseCompleted
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Companion.BASE_SCRIPT_MODEL_PHASE
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase.Companion.SCRIPT_MODEL_PHASE
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.buildScriptName
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleWrapper
import org.jetbrains.plugins.gradle.testFramework.projectInfo.initProject
import org.jetbrains.plugins.gradle.testFramework.projectInfo.settingsScriptName
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaRootModuleInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleSettingsFile
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_BASE_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_BASE_SCRIPTS_MODEL_IMPORT_UNSUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedClass
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate


@TestApplication
class KotlinGradleScriptImportingTest {

    private val testRoot by tempPathFixture()

    @Nested
    @ParameterizedClass
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_BASE_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    inner class BaseScriptModelSupported(gradleVersion: GradleVersion) {

        private val gradle by gradleFixture(gradleVersion)

        @Test
        fun `test script entities from all linked builds should be preserved in workspace model`(): Unit =
            `test script entities from all linked builds should be preserved in workspace model`(gradle)

        @Test
        fun `test script entities visibility at sync phases`(): Unit =
            `test script entities visibility at sync phases`(gradle)

        @Test
        fun `test base script model is applied to files not opened in editor`() =
            `test base script model is applied to files not opened in editor`(gradle)

        @Test
        fun `test base script model is loaded when settings script has compilation errors`() =
            `test base script model is loaded when settings script has compilation errors`(gradle)

        @Test
        fun `test Kotlin DSL sync phases order`() =
            `test Kotlin DSL sync phases order`(gradle)
    }

    @Nested
    @ParameterizedClass
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_BASE_SCRIPTS_MODEL_IMPORT_UNSUPPORTED_VERSIONS)
    inner class BaseScriptModelUnsupported(gradleVersion: GradleVersion) {

        private val gradle by gradleFixture(gradleVersion)

        @Test
        fun `test script entities from all linked builds should be preserved in workspace model`(): Unit =
            `test script entities from all linked builds should be preserved in workspace model`(gradle)

        @Test
        fun `test script entities visibility at sync phases`(): Unit =
            `test script entities visibility at sync phases`(gradle)

        @Test
        fun `test Kotlin DSL sync phases order`() =
            `test Kotlin DSL sync phases order`(gradle)
    }

    fun `test base script model is applied to files not opened in editor`(gradle: GradleTestFixture) = runBlocking {
        val projectInfo = simpleJavaProjectInfo(gradle.gradleVersion)
        val projectRoot = projectInfo.initProject(testRoot)

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

    fun `test base script model is loaded when settings script has compilation errors`(gradle: GradleTestFixture) = runBlocking {
        val projectInfo = gradleProjectInfo(gradle.gradleVersion) {
            gradleWrapper()
            simpleSettingsFile {
                addCode("unresolvedSettingsScriptReference()")
            }
            simpleJavaRootModuleInfo()
        }
        val projectRoot = projectInfo.initProject(testRoot)

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

    fun `test Kotlin DSL sync phases order`(gradle: GradleTestFixture) = runBlocking {
        val projectInfo = simpleJavaProjectInfo(gradle.gradleVersion)
        val projectRoot = projectInfo.initProject(testRoot)
        val expectedPhases = buildList {
            add(GradleSyncPhase.INITIAL_PHASE)
            if (GradleVersionSpecificsUtil.isBaseScriptModelSupported(gradle.gradleVersion)) {
                add(BASE_SCRIPT_MODEL_PHASE)
            }
            add(GradleSyncPhase.PROJECT_MODEL_PHASE)
            add(GradleSyncPhase.SOURCE_SET_MODEL_PHASE)
            add(SCRIPT_MODEL_PHASE)
            add(GradleSyncPhase.ADDITIONAL_MODEL_PHASE)
        }
        val completedPhases = CopyOnWriteArrayList<GradleSyncPhase>()

        whenSyncPhaseCompleted(asDisposable()) { _, phase ->
            if (phase in expectedPhases) {
                completedPhases.add(phase)
            }
        }

        gradle.openProject(projectRoot).useProjectAsync {
            assertEquals(expectedPhases, completedPhases) {
                "Kotlin DSL sync phases should be completed together with default Gradle sync phases"
            }
        }
    }

    fun `test script entities from all linked builds should be preserved in workspace model`(gradle: GradleTestFixture) = runBlocking {
        val project1Info = simpleJavaProjectInfo(gradle.gradleVersion, "project1")
        val project2Info = simpleJavaProjectInfo(gradle.gradleVersion, "project2")
        val project1Root = project1Info.initProject(testRoot)
        val project2Root = project2Info.initProject(testRoot)

        val projectScripts = sequenceOf(
            project1Root.resolve(project1Info.rootModule.settingsScriptName),
            project1Root.resolve(project1Info.rootModule.buildScriptName),
            project2Root.resolve(project2Info.rootModule.settingsScriptName),
            project2Root.resolve(project2Info.rootModule.buildScriptName),
        )

        gradle.openProject(project1Root).useProjectAsync { project ->
            gradle.linkProject(project, project2Root)

            val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
            val expectedScripts = projectScripts.map { it.toVirtualFileUrl(virtualFileUrlManager) }
            val actualScripts = project.workspaceModel.currentSnapshot.entities<KotlinScriptEntity>()
                .map { it.virtualFileUrl }
            assertEqualsUnordered(expectedScripts.toList(), actualScripts.toList()) {
                "Incorrect list of KotlinScriptEntity generated during Gradle sync"
            }
        }
    }

    fun `test script entities visibility at sync phases`(gradle: GradleTestFixture) = runBlocking {
        val projectInfo = simpleJavaProjectInfo(gradle.gradleVersion)
        val projectRoot = projectInfo.initProject(testRoot)

        val entitiesAtPhases = collectScriptEntityPhasesAtSyncPhases(asDisposable())

        fun assertEntities(expectedEntityAtPhase: GradleSyncPhase?, filter: Predicate<GradleSyncPhase>) {
            for ((phase, actualEntitiesAtPhase) in entitiesAtPhases.entries) {
                if (filter.test(phase)) {
                    when (expectedEntityAtPhase) {
                        null -> assertEmpty(actualEntitiesAtPhase) {
                            "Unexpected script entities from $expectedEntityAtPhase at $phase ($filter)"
                        }
                        else -> assertSingle(expectedEntityAtPhase, actualEntitiesAtPhase) {
                            "Expected script entities from $expectedEntityAtPhase at $phase ($filter)"
                        }
                    }
                }
            }
        }

        gradle.openProject(projectRoot).useProjectAsync { project ->

            if (GradleVersionSpecificsUtil.isBaseScriptModelSupported(gradle.gradleVersion)) {
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
            } else {
                assertEntities(null, object : Predicate<GradleSyncPhase> {
                    override fun toString(): String = "during initial sync before $SCRIPT_MODEL_PHASE"
                    override fun test(phase: GradleSyncPhase): Boolean = phase < SCRIPT_MODEL_PHASE
                })
                assertEntities(SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                    override fun toString(): String = "during initial sync after $SCRIPT_MODEL_PHASE"
                    override fun test(phase: GradleSyncPhase): Boolean = phase >= SCRIPT_MODEL_PHASE
                })
            }

            entitiesAtPhases.clear()
            gradle.syncProject(project, projectRoot)

            if (GradleVersionSpecificsUtil.isBaseScriptModelSupported(gradle.gradleVersion)) {
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
            } else {
                assertEntities(SCRIPT_MODEL_PHASE, object : Predicate<GradleSyncPhase> {
                    override fun toString(): String = "during re-sync"
                    override fun test(phase: GradleSyncPhase): Boolean = true
                })
            }
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
