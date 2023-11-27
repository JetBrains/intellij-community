// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import org.jetbrains.kotlin.scripting.definitions.*
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.toScriptSource


/**
 * [ScriptDefinitionsManager] is a project service responsible for loading/caching and searching script definitions.
 *
 * Class delegates the loading to the instances of [ScriptDefinitionsSource] which in their turn are registered via extension points
 * either as [ScriptTemplatesProviderAdapter] or [ScriptDefinitionContributor].
 *
 * Note that the class is `open` for inheritance only for the testing purpose. Its dependencies are cut via a set of `protected open` methods.
 */
open class ScriptDefinitionsManager(private val project: Project) : LazyScriptDefinitionProvider(), Disposable {

    companion object {
        fun getInstance(project: Project): ScriptDefinitionsManager =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionsManager
    }

    // Support for insertion order is crucial because 'getSources()' is based on EP order in XML (default configuration source goes last)
    private val definitionsBySource = mutableMapOf<ScriptDefinitionsSource, List<ScriptDefinition>>()

    private val activatedDefinitionSources: MutableSet<ScriptDefinitionsSource> = ConcurrentHashMap.newKeySet()

    private val failedContributorsHashes: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    private val definitionsLock = ReentrantLock()

    @Volatile
    private var definitions: List<ScriptDefinition>? = null

    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val scriptingSettings = kotlinScriptingSettingsSafe() ?: return emptySequence()
            return getOrLoadDefinitions().asSequence().filter { scriptingSettings.isScriptDefinitionEnabled(it) }
        }


    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null

        tryGetScriptDefinitionFast(locationId)?.let { fastPath -> return fastPath }

        getOrLoadDefinitions()

        val definition =
            if (isScratchFile(script)) {
                // Scratch should always have the default script definition
                getDefaultDefinition()
            } else {
                super.findDefinition(script) // Some embedded scripts (e.g., Kotlin Notebooks) have their own definition
                    ?: if (isEmbeddedScript(script)) getDefaultDefinition() else null
            }

        return definition
    }

    @Deprecated("Migrating to configuration refinement", level = DeprecationLevel.ERROR)
    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? {
        @Suppress("DEPRECATION")
        return findDefinition(File(fileName).toScriptSource())?.legacyDefinition
    }

    fun getAllDefinitions(): List<ScriptDefinition> = getOrLoadDefinitions()

    fun reloadScriptDefinitionsIfNeeded() = getOrLoadDefinitions()

    fun reloadScriptDefinitions() = reloadDefinitionsInternal(getSources())

    fun isReady(): Boolean = true

    fun reloadDefinitionsBy(source: ScriptDefinitionsSource): List<ScriptDefinition> = reloadDefinitionsInternal(listOf(source))

    fun reorderScriptDefinitions() {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: return
        if (definitions == null) return

        withLocks {
            definitions?.let { list ->
                list.forEach {
                    it.order = scriptingSettings.getScriptDefinitionOrder(it)
                }
                definitions = list.sortedBy(ScriptDefinition::order)
            }
            clearCache()
        }

        applyDefinitionsUpdate()  // <== acquires read-action inside
    }

    override fun getDefaultDefinition(): ScriptDefinition {
        val bundledScriptDefinitionContributor = getBundledScriptDefinitionContributor()
            ?: error("StandardScriptDefinitionContributor should be registered in plugin.xml")
        return ScriptDefinition.FromLegacy(getScriptingHostConfiguration(), bundledScriptDefinitionContributor.getDefinitions().last())
    }

    // This function is aimed to fix locks acquisition order.
    // The internal block still may acquire the read lock, it just won't have an effect.
    private fun withLocks(block: () -> Unit) = executeUnderReadLock { definitionsLock.withLock { block.invoke() } }

    private fun getOrLoadDefinitions(): List<ScriptDefinition> {
        // This is not thread safe, but if the condition changes by the time of the "then do this" it's ok - we just refresh the data.
        // Taking local lock here is dangerous due to the possible global read-lock acquisition (hence, the deadlock). See KTIJ-27838.
        return if (definitions == null || !allDefinitionSourcesContributedToCache()) {
            reloadDefinitionsInternal(getSources())
        } else {
            definitions ?: error("'definitions' became null after they weren't")
        }
    }

    private fun allDefinitionSourcesContributedToCache(): Boolean = activatedDefinitionSources.containsAll(getSources())

    private fun reloadDefinitionsInternal(sources: List<ScriptDefinitionsSource>): List<ScriptDefinition> {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: error("Kotlin script setting not found")

        var loadedDefinitions: List<ScriptDefinition>? = null

        val (ms, newDefinitionsBySource) = measureTimeMillisWithResult {
            sources.associateWith {
                val (ms, definitions) = measureTimeMillisWithResult { it.safeGetDefinitions() /* can acquire read-action inside */ }
                scriptingDebugLog { "Loaded definitions: time = $ms ms, source = ${it.javaClass.name}, definitions = ${definitions.map { it.name }}" }
                definitions
            }
        }

        scriptingDebugLog { "Definitions loading total time: $ms ms" }

        if (newDefinitionsBySource.isEmpty())
            return emptyList()

        withLocks {
            definitionsBySource.putAll(newDefinitionsBySource)

            loadedDefinitions = definitionsBySource.values.flattenTo(mutableListOf())
                .onEach { it.order = scriptingSettings.getScriptDefinitionOrder(it) }
                .sortedBy(ScriptDefinition::order)
                .takeIf { it.isNotEmpty() }

            definitions = loadedDefinitions
            clearCache()
        }

        activatedDefinitionSources.addAll(sources)

        applyDefinitionsUpdate() // <== acquires read-action inside

        return loadedDefinitions ?: emptyList()
    }

    private fun ScriptDefinitionsSource.safeGetDefinitions(): List<ScriptDefinition> {
        if (!failedContributorsHashes.contains(hashCode())) try {
            return definitions.toList()
        } catch (t: Throwable) {
            if (t is ControlFlowException) throw t
            // reporting failed loading only once
            failedContributorsHashes.add(hashCode())
            scriptingErrorLog("Cannot load script definitions from $this: ${t.cause?.message ?: t.message}", t)
        }
        return emptyList()
    }

    private fun isEmbeddedScript(code: SourceCode): Boolean {
        val scriptSource = code as? VirtualFileScriptSource ?: return false
        val virtualFile = scriptSource.virtualFile
        return virtualFile is VirtualFileWindow && virtualFile.fileType == KotlinFileType.INSTANCE
    }

    private fun associateFileExtensionsIfNeeded() {
        val fileTypeManager = FileTypeManager.getInstance()
        val newExtensions = getKnownFilenameExtensions().toSet().filter {
            val fileTypeByExtension = fileTypeManager.getFileTypeByFileName("xxx.$it")
            val notKnown = fileTypeByExtension != KotlinFileType.INSTANCE
            if (notKnown) {
                scriptingWarnLog("extension $it file type [${fileTypeByExtension.name}] is not registered as ${KotlinFileType.INSTANCE.name}")
            }
            notKnown
        }.toSet()

        if (newExtensions.isNotEmpty()) {
            scriptingWarnLog("extensions ${newExtensions} is about to be registered as ${KotlinFileType.INSTANCE.name}")
            // Register new file extensions
            ApplicationManager.getApplication().invokeLater {
                runWriteAction {
                    newExtensions.forEach {
                        fileTypeManager.associateExtension(KotlinFileType.INSTANCE, it)
                    }
                }
            }
        }
    }

    override fun dispose() {
        super.dispose()

        clearCache()

        definitionsBySource.clear()
        definitions = null
        activatedDefinitionSources.clear()
        failedContributorsHashes.clear()
    }

    // FOR TESTS ONLY: we introduce a possibility to cut dependencies over inheritance

    protected open fun getSources(): List<ScriptDefinitionsSource> {
        @Suppress("DEPRECATION")
        val fromDeprecatedEP = project.extensionArea.getExtensionPoint(ScriptTemplatesProvider.EP_NAME).extensionList
            .map { ScriptTemplatesProviderAdapter(it).asSource() }
        val fromNewEp = ScriptDefinitionContributor.EP_NAME.getPoint(project).extensionList
            .map { it.asSource() }
        return fromNewEp.dropLast(1) + fromDeprecatedEP + fromNewEp.last()
    }

    protected open fun kotlinScriptingSettingsSafe(): KotlinScriptingSettings? {
        return runReadAction {
            if (!project.isDisposed) KotlinScriptingSettings.getInstance(project) else null
        }
    }

    protected open fun tryGetScriptDefinitionFast(locationId: String): ScriptDefinition? {
        return ScriptConfigurationManager.compositeScriptConfigurationManager(project)
            .tryGetScriptDefinitionFast(locationId)
    }

    protected open fun applyDefinitionsUpdate() {
        associateFileExtensionsIfNeeded()
        ScriptConfigurationManager.getInstance(project).updateScriptDefinitionReferences()
    }

    protected open fun isScratchFile(script: SourceCode): Boolean {
        val virtualFile =
            if (script is VirtualFileScriptSource) script.virtualFile
            else script.locationId?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        return virtualFile != null && ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType
    }

    protected open fun getBundledScriptDefinitionContributor() =
        ScriptDefinitionContributor.find<BundledScriptDefinitionContributor>(project)

    protected open fun executeUnderReadLock(block: () -> Unit) = runReadAction { block() }

    // FOR TESTS ONLY: END
}