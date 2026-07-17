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
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptProcessingFilter
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.importScripts
import kotlin.script.experimental.api.refineConfiguration
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

@Target(AnnotationTarget.FILE)
private annotation class CircularImport(val path: String)

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

    fun `test load does not create entities for circular imported scripts`() = runBlocking {
        registerCircularImportScriptDefinition()

        val first = myFixture.tempDirFixture.createFile(
            "first.imports.kts",
            """
            @file:CircularImport("second.imports.kts")

            val first = 1
            """.trimIndent(),
        )
        val second = myFixture.tempDirFixture.createFile(
            "second.imports.kts",
            """
            @file:CircularImport("first.imports.kts")

            val second = 2
            """.trimIndent(),
        )

        KotlinScriptService.getInstance(project).load(first)

        assertNull(
            "Circular imported scripts must not create an entity for the entry script",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, first)
        )
        assertNull(
            "Circular imported scripts must not create an entity for transitively imported scripts",
            KotlinScriptEntityProvider.findKotlinScriptEntity(project, second)
        )
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

    @Suppress("DEPRECATION") // ScriptDefinitionsSource is the registration path used by the script test fixtures (KT-82551).
    private fun registerCircularImportScriptDefinition() {
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("imports.kts")
                ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
                refineConfiguration {
                    onAnnotations(CircularImport::class) { context -> resolveImportedScripts(context) }
                }
            },
        )

        val definition = ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            compilationConfiguration,
            evaluationConfiguration,
        )

        project.registerExtension(
            SCRIPT_DEFINITIONS_SOURCES,
            object : ScriptDefinitionsSource {
                override val definitions: Sequence<ScriptDefinition> = sequenceOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    private fun resolveImportedScripts(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val scriptDir = context.script.locationId?.let { File(it).parentFile }
            ?: return context.compilationConfiguration.asSuccess()

        val imported = context.collectedData?.get(ScriptCollectedData.collectedAnnotations).orEmpty()
            .map { it.annotation }
            .filterIsInstance<CircularImport>()
            .map { FileScriptSource(File(scriptDir, it.path)) }

        if (imported.isEmpty()) return context.compilationConfiguration.asSuccess()
        return context.compilationConfiguration.with { importScripts(imported) }.asSuccess()
    }

}
