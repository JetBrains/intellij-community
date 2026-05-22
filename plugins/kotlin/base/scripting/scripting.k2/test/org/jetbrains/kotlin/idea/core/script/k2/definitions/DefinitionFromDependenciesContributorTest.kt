// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.components.service
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.TestDataPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("base/scripting/scripting.k2")
@TestDataPath($$"$CONTENT_ROOT")
@TestMetadata("testData/script/templatesFromDependencies")
class DefinitionFromDependenciesContributorTest : AbstractDefinitionFromDependenciesContributorTest() {

    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    private fun runTest(path: String) {
        KotlinTestUtils.runTest({ doTest(it) }, this, path)
    }

    @TestMetadata("singleTemplate")
    fun testSingleTemplate() {
        runTest("testData/script/templatesFromDependencies/singleTemplate/")
    }

    @TestMetadata("multipleTemplates")
    fun testMultipleTemplates() {
        runTest("testData/script/templatesFromDependencies/multipleTemplates/")
    }

    @TestMetadata("multipleRoots")
    fun testMultipleRoots() {
        runTest("testData/script/templatesFromDependencies/multipleRoots/")
    }

    @TestMetadata("inJar")
    fun testInJar() {
        runTest("testData/script/templatesFromDependencies/inJar/")
    }

    @TestMetadata("inTests")
    fun testInTests() {
        runTest("testData/script/templatesFromDependencies/inTests/")
    }

    @TestMetadata("outsideRoots")
    fun testOutsideRoots() {
        runTest("testData/script/templatesFromDependencies/outsideRoots/")
    }

    fun testReRunUpdatesExistingEntity() {
        runTest("testData/script/templatesFromDependencies/singleTemplate/")

        val before = project.workspaceModel.currentSnapshot
            .entities(ScriptDefinitionTemplateEntity::class.java).toList()
        assertEquals("Expected exactly one entity after first contribute", 1, before.size)

        runBlocking {
            val contributor = project.service<DefinitionFromDependenciesContributor>()
            val discovered = contributor.discoverDefinitionTemplates()
            contributor.updateWorkspaceModel(discovered)
        }

        val after = project.workspaceModel.currentSnapshot
            .entities(ScriptDefinitionTemplateEntity::class.java).toList()
        assertEquals("Re-running contributor must not create duplicate entities", 1, after.size)
        assertEquals(
            "Re-running with the same project structure must not change templateFqns",
            before.single().templateFqns.sorted(),
            after.single().templateFqns.sorted()
        )
    }

    fun testContributorBumpsModificationTracker() {
        val before = ScriptDefinitionsModificationTracker.getInstance(project).modificationCount
        runTest("testData/script/templatesFromDependencies/singleTemplate/")
        val after = ScriptDefinitionsModificationTracker.getInstance(project).modificationCount

        assertTrue(
            "ScriptDefinitionsModificationTracker must advance after the contributor writes a new entity " +
                    "(was $before, now $after) — verifies the WorkspaceModelChangeListener wiring",
            after > before
        )
    }

    fun testDiscoverIsPureNoEntityWrite() {
        val entitiesBefore = project.workspaceModel.currentSnapshot
            .entities(ScriptDefinitionTemplateEntity::class.java).toList().size

        runBlocking {
            project.service<DefinitionFromDependenciesContributor>().discoverDefinitionTemplates()
        }

        val entitiesAfter = project.workspaceModel.currentSnapshot
            .entities(ScriptDefinitionTemplateEntity::class.java).toList().size
        assertEquals(
            "discoverDefinitionTemplates must not touch the workspace model",
            entitiesBefore,
            entitiesAfter
        )
    }
}
