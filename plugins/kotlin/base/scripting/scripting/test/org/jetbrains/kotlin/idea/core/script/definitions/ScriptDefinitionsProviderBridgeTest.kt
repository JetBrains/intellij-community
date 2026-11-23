// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.findEntitiesByVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition as IdeScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.io.File
import java.net.URLClassLoader
import java.nio.file.attribute.FileTime
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.setLastModifiedTime
import kotlin.io.path.writeBytes
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.templates.ScriptWithArgs
import kotlin.time.Duration.Companion.minutes

class ScriptDefinitionsProviderBridgeTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun `test definitions from the provider EP reach the definition provider`() {
        registerProvidedDefinition()

        val extensions = ScriptDefinitionProviderImpl.getInstance(project).currentDefinitions.map { it.fileExtension }.toList()
        assertContainsElements(extensions, PROVIDED_EXTENSION)
    }

    fun `test a lookup gives the definitions of the last load`() {
        var provided = emptyList<ScriptDefinition>()
        registerProvider { provided }
        val definitionProvider = ScriptDefinitionProviderImpl.getInstance(project)
        val loaded = definitionProvider.cachedProvidedDefinitions
        provided = listOf(providedDefinition())

        assertSame("A lookup must not load the definitions", loaded, definitionProvider.cachedProvidedDefinitions)
        definitionProvider.scheduleLoading()
        runBlocking { definitionProvider.awaitLoading() }
        assertContainsElements(definitionProvider.currentDefinitions.map { it.fileExtension }.toList(), PROVIDED_EXTENSION)
    }

    fun `test only a script that gets another definition is outdated, and a reload updates it`() {
        val kept = myFixture.addFileToProject("plain.kts", "").virtualFile
        val claimed = myFixture.addFileToProject("script.$PROVIDED_EXTENSION", "").virtualFile
        val scriptService = KotlinScriptService.getInstance(project)
        runBlocking {
            scriptService.load(kept)
            scriptService.load(claimed)
        }

        registerProvidedDefinition()

        assertTrue("A script that gets another definition must be outdated", scriptService.hasOutdatedDefinition(claimed))
        assertFalse("A script that keeps its definition must not be outdated", scriptService.hasOutdatedDefinition(kept))
        runBlocking { scriptService.reload(claimed) }
        assertFalse("A reload must update the definition", scriptService.hasOutdatedDefinition(claimed))
    }

    fun `test a load reloads an open script that gets another definition`() {
        val claimed = myFixture.addFileToProject("open.$PROVIDED_EXTENSION", "").virtualFile
        runInEdtAndWait { myFixture.openFileInEditor(claimed) }
        val scriptService = KotlinScriptService.getInstance(project)
        runBlocking { scriptService.load(claimed) }

        registerProvidedDefinition()

        runBlocking {
            withTimeout(1.minutes) {
                while (claimed.scriptEntities().isEmpty() || scriptService.hasOutdatedDefinition(claimed)) delay(10)
            }
        }
    }

    fun `test a lookup gives the definition of the script entity until a reload`() {
        val script = myFixture.addFileToProject("pinned.$PROVIDED_EXTENSION", "").virtualFile
        val source = VirtualFileScriptSource(script)
        val scriptService = KotlinScriptService.getInstance(project)
        runBlocking { scriptService.load(script) }
        val definitionProvider = ScriptDefinitionProviderImpl.getInstance(project)
        val loadedWith = definitionProvider.findDefinition(source)?.definitionId

        registerProvidedDefinition()

        val matching = definitionProvider.findMatchingDefinition(source).definitionId
        assertTrue("The new definition must match the script", matching != loadedWith)
        assertEquals("A lookup must give the definition of the entity", loadedWith, definitionProvider.findDefinition(source)?.definitionId)
        runBlocking { scriptService.reload(script) }
        assertEquals("After a reload, a lookup must give the new definition", matching, definitionProvider.findDefinition(source)?.definitionId)
    }

    fun `test a script entity of another owner is never outdated`() {
        val foreign = myFixture.addFileToProject("foreign.$PROVIDED_EXTENSION", "").virtualFile
        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        runBlocking {
            project.workspaceModel.update("add a script entity of another owner") { storage ->
                storage addEntity KotlinScriptEntity(foreign.toVirtualFileUrl(urlManager), emptyList(), "foreign", 0, NonPersistentEntitySource)
            }
        }
        try {
            registerProvidedDefinition()

            assertFalse("A script of another owner must not be outdated", KotlinScriptService.getInstance(project).hasOutdatedDefinition(foreign))
            assertEquals(listOf(NonPersistentEntitySource), foreign.scriptEntities().map { it.entitySource })
        }
        finally {
            runBlocking {
                project.workspaceModel.update("remove the script entity of another owner") { storage ->
                    foreign.scriptEntities().forEach { storage.removeEntity(it) }
                }
            }
        }
    }

    private fun VirtualFile.scriptEntities(): List<KotlinScriptEntity> =
        project.workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
            .findEntitiesByVirtualFile(this, project.workspaceModel.getVirtualFileUrlManager())
            .filterIsInstance<KotlinScriptEntity>()
            .toList()

    fun `test a rebuilt template class in a directory changes the fingerprint, and a definition without a template class has none`() {
        val classDirectory = createTempDirectory("templateClasses")
        try {
            val templateName = ScriptWithArgs::class.java.name
            val classFile = classDirectory.resolve(templateName.replace('.', '/') + ".class")
            classFile.createParentDirectories()
            ScriptWithArgs::class.java.getResourceAsStream(ScriptWithArgs::class.java.simpleName + ".class")!!.use { classFile.writeBytes(it.readBytes()) }
            val templateClass = URLClassLoader(arrayOf(classDirectory.toUri().toURL()), null).loadClass(templateName).kotlin
            fun definition() = IdeScriptDefinition.FromConfigurations(
                defaultJvmScriptingHostConfiguration,
                ScriptCompilationConfiguration { baseClass(KotlinType(templateClass)) },
                null,
            )

            val built = definition().templateFingerprint
            classFile.setLastModifiedTime(FileTime.fromMillis(classFile.getLastModifiedTime().toMillis() + 2000))

            assertTrue("A class from a directory must have a fingerprint", built != 0L)
            assertTrue("A rebuilt template must change the fingerprint", definition().templateFingerprint != built)
            val withoutClass = IdeScriptDefinition.FromConfigurations(
                defaultJvmScriptingHostConfiguration,
                ScriptCompilationConfiguration { baseClass(KotlinType("org.example.Missing")) },
                null,
            )
            assertEquals(0L, withoutClass.templateFingerprint)
            fun definitionWithClasspath(jar: String) = IdeScriptDefinition.FromConfigurations(
                ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) { configurationDependencies(JvmDependency(File(jar))) },
                ScriptCompilationConfiguration { baseClass(KotlinType("org.example.Missing")) },
                null,
            )
            assertTrue(
                "Another template classpath must change the fingerprint",
                definitionWithClasspath("a.jar").templateFingerprint != definitionWithClasspath("b.jar").templateFingerprint,
            )
        }
        finally {
            NioFiles.deleteRecursively(classDirectory)
        }
    }

    fun `test definitions from the provider EP take precedence over the bundled default`() {
        registerProvidedDefinition()

        val definitions = ScriptDefinitionProviderImpl.getInstance(project).currentDefinitions.toList()
        val provided = definitions.indexOfFirst { it.fileExtension == PROVIDED_EXTENSION }
        val bundled = definitions.indexOfFirst { it.isDefault }

        assertTrue("The definition bridged from the provider EP must be present", provided >= 0)
        assertTrue("The bundled default definition must be present and sort last", bundled >= 0)
        assertTrue(
            "Definitions bridged from the provider EP must keep order = Int.MIN_VALUE and sort before the bundled default",
            provided < bundled,
        )
    }

    fun `test filePathPattern narrows the extension based match`() {
        val filtered = createScriptDefinitionFromTemplate(
            KotlinType(ScriptWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension(FILTERED_EXTENSION)
                filePathPattern(".*/accepted/.*")
            },
        )
        registerProvider(filtered)

        val definitionProvider = project.service<ScriptDefinitionProvider>()
        assertEquals(
            "A file outside filePathPattern must fall through to the bundled default definition",
            true,
            definitionProvider.findDefinition(FileScriptSource(File("/rejected/script.$FILTERED_EXTENSION")))?.isDefault,
        )
        assertEquals(
            "A file inside filePathPattern must be claimed by the filtered definition",
            false,
            definitionProvider.findDefinition(FileScriptSource(File("/accepted/script.$FILTERED_EXTENSION")))?.isDefault,
        )
    }

    private fun registerProvidedDefinition() = registerProvider(providedDefinition())

    private fun providedDefinition(): ScriptDefinition = createScriptDefinitionFromTemplate(
        KotlinType(ScriptWithArgs::class),
        defaultJvmScriptingHostConfiguration,
        compilation = { fileExtension(PROVIDED_EXTENSION) },
    )

    private fun registerProvider(definition: ScriptDefinition) = registerProvider { listOf(definition) }

    private fun registerProvider(definitions: () -> List<ScriptDefinition>) {
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "ScriptDefinitionsProviderBridgeTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = definitions()
            },
            testRootDisposable,
        )
        runBlocking { ScriptDefinitionProviderImpl.getInstance(project).awaitLoading() }
    }

    companion object {
        private const val PROVIDED_EXTENSION = "provided.kts"
        private const val FILTERED_EXTENSION = "filtered.kts"
    }
}
