// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.testFramework.workspaceModel.update
import org.jetbrains.kotlin.gradle.scripting.importing.GradleKotlinDslScriptEntitySource
import org.jetbrains.kotlin.gradle.scripting.importing.KotlinDslScriptSyncExtension
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntity
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.service.syncAction.impl.extensions.GradleBaseSyncExtension
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectResolverContext.Companion.projectResolverContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class GradleScriptNotificationProviderTest {
    private val project = projectFixture()
    private val buildA = project.moduleFixture("project-a").sourceRootFixture().virtualFileFixture("build.gradle.kts", "plugins { java }")
    private val buildB = project.moduleFixture("project-b").sourceRootFixture().virtualFileFixture("build.gradle.kts", "plugins { java }")
    private val buildC = project.moduleFixture("project-c").sourceRootFixture().virtualFileFixture("build.gradle.kts", "plugins { java }")
    private val provider = GradleScriptNotificationProvider()

    @Test
    fun `unlinked script has a notification before and after generic script loading`() = timeoutRunBlocking {
        assertNotNull(provider.collectNotificationData(project.get(), buildA.get()))
        registerScript(buildA.get(), KotlinScriptEntitySource)
        assertNotNull(provider.collectNotificationData(project.get(), buildA.get()))
    }

    @Test
    fun `linking successive independent builds keeps notifications for remaining builds`() = timeoutRunBlocking {
        registerImportedScript(buildA.get())
        assertNull(provider.collectNotificationData(project.get(), buildA.get()))

        assertNotNull(provider.collectNotificationData(project.get(), buildB.get()))
        registerImportedScript(buildB.get())
        assertNull(provider.collectNotificationData(project.get(), buildB.get()))

        registerScript(buildC.get(), KotlinScriptEntitySource)
        repeat(2) {
            assertNotNull(provider.collectNotificationData(project.get(), buildC.get()))
        }
        registerImportedScript(buildC.get())
        assertNull(provider.collectNotificationData(project.get(), buildC.get()))
    }

    @Test
    fun `Gradle import replaces a generic script while preserving other unlinked scripts`() = timeoutRunBlocking {
        registerScript(buildA.get(), KotlinScriptEntitySource)
        registerScript(buildB.get(), KotlinScriptEntitySource)

        registerImportedScript(buildA.get())

        val entities = project.get().workspaceModel.currentSnapshot.entities(KotlinScriptEntity::class.java).toList()
        assertEquals(2, entities.size)
        assertNull(provider.collectNotificationData(project.get(), buildA.get()))
        assertNotNull(provider.collectNotificationData(project.get(), buildB.get()))
    }

    private suspend fun registerImportedScript(file: VirtualFile) {
        val context = object : ProjectResolverContext by projectResolverContext(project.get()) {
            override val projectPath: String = file.parent.path
        }
        val phase = GradleSyncPhase.SCRIPT_MODEL_PHASE
        val model = project.get().workspaceModel
        val url = file.toVirtualFileUrl(model.getVirtualFileUrlManager())
        val syncStorage = MutableEntityStorage.create()
        syncStorage.addEntity(KotlinScriptEntity(url, emptyList(), GradleKotlinDslScriptEntitySource(context.projectPath, phase)))
        model.update { storage ->
            KotlinDslScriptSyncExtension().updateProjectModel(context, syncStorage, storage, phase)
            GradleBaseSyncExtension().updateProjectModel(context, syncStorage, storage, phase)
        }
    }

    private suspend fun registerScript(file: VirtualFile, source: EntitySource) {
        val model = project.get().workspaceModel
        val url = file.toVirtualFileUrl(model.getVirtualFileUrlManager())
        model.update { storage ->
            storage.entities(KotlinScriptEntity::class.java).filter { it.virtualFileUrl == url }.toList().forEach(storage::removeEntity)
            storage.addEntity(KotlinScriptEntity(url, emptyList(), source))
        }
    }
}
