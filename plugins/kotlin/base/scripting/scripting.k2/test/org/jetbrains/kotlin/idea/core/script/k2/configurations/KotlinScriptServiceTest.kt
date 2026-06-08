// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.workspaceModel.update
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptProcessingFilter
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinScriptServiceTest : KotlinLightCodeInsightFixtureTestCase() {

    

    override fun runInDispatchThread(): Boolean = false

    fun `test load creates workspace model entity for kts file`() {
        val file = myFixture.configureByText("test.kts", "val x = 1")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
        }

        assertNotNull(
            "KotlinScriptEntity must be created after load",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile)
        )
    }

    fun `test load is idempotent`() {
        val file = myFixture.configureByText("test.kts", "val x = 1")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
            KotlinScriptService.getInstance(project).load(file.virtualFile)
        }

        val entities = project.workspaceModel.currentSnapshot
            .entities(KotlinScriptEntity::class.java)
            .filter { it.virtualFileUrl.url.endsWith("test.kts") }
            .toList()
        assertEquals("Exactly one entity must exist after loading the same file twice", 1, entities.size)
    }

    fun `test load skips file rejected by processing filter`() {
        project.registerExtension(
            KotlinScriptProcessingFilter.EP_NAME,
            object : KotlinScriptProcessingFilter {
                override fun shouldProcessScript(virtualFile: VirtualFile): Boolean = false
            },
            testRootDisposable
        )

        val file = myFixture.configureByText("test2.kts", "val x = 1")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
        }

        assertNull(
            "KotlinScriptEntity must NOT be created when filter rejects the file",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile)
        )
    }

    fun `test reload removes existing entity and creates a new one`() {
        val file = myFixture.configureByText("test.kts", "val x = 1")

        runBlocking {
            KotlinScriptService.getInstance(project).load(file.virtualFile)
        }
        assertNotNull(KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile))

        runBlocking {
            KotlinScriptService.getInstance(project).reload(file.virtualFile)
        }

        assertNotNull(
            "KotlinScriptEntity must exist after reload",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile)
        )
    }

    fun `test reload increments ScriptDefinitionsModificationTracker`() {
        val file = myFixture.configureByText("test.kts", "val x = 1")
        val tracker = ScriptDefinitionsModificationTracker.getInstance(project)

        val countBefore = tracker.modificationCount

        runBlocking {
            KotlinScriptService.getInstance(project).reload(file.virtualFile)
        }

        assertTrue(
            "ScriptDefinitionsModificationTracker must be incremented by reload",
            tracker.modificationCount > countBefore
        )
    }

    fun `test scheduleReloadOpenScripts invalidates stale closed scripts and reloads open scripts`() = runBlocking {
        val openFile = myFixture.configureByText("open.kts", "val open = 1")
        val closedFile = myFixture.addFileToProject("closed.kts", "val closed = 2")
        val service = KotlinScriptService.getInstance(project)
        val tracker = ScriptDefinitionsModificationTracker.getInstance(project)

        service.load(openFile.virtualFile)
        service.load(closedFile.virtualFile)

        val countBefore = tracker.modificationCount

        service.scheduleReloadOpenScripts().join()

        assertTrue("scheduleReloadOpenScripts must increment ScriptDefinitionsModificationTracker", tracker.modificationCount > countBefore)
        assertNotNull(
            "Open script must be reloaded after scheduleReloadOpenScripts",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, openFile.virtualFile)
        )
        assertNull(
            "Closed script entity must stay invalidated until the file is opened again",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, closedFile.virtualFile)
        )
    }

    fun `test workspace model listener drops script caches before script entity change`() = runBlocking {
        val file = myFixture.addFileToProject("test.kts", "val x = 1")
        KotlinScriptService.getInstance(project).load(file.virtualFile)

        val tracker = ScriptDependenciesModificationTracker.getInstance(project)
        val countBefore = tracker.modificationCount

        val entity = requireNotNull(KotlinScriptEntityProvider.findKotlinScriptEntity(project, file.virtualFile))
        project.workspaceModel.update { storage ->
            storage.removeEntity(entity)
        }

        assertTrue(
            "Workspace model listener must drop Kotlin script caches before entity changes are applied",
            tracker.modificationCount > countBefore
        )
    }

}
