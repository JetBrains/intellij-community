// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.registerExtension
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptProcessingFilter
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class KotlinScriptServiceTest : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

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
}
