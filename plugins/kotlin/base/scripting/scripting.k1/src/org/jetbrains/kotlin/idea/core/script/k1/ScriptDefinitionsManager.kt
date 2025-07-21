// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

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
import org.jetbrains.kotlin.idea.core.script.shared.definition.defaultDefinition
import org.jetbrains.kotlin.idea.core.script.k1.settings.KotlinScriptingSettingsImpl
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.v1.IdeScriptDefinitionProvider
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingErrorLog
import org.jetbrains.kotlin.idea.core.script.v1.scriptingWarnLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.SourceCode

/**
 * [ScriptDefinitionsManager] is a project service responsible for loading/caching and searching [org.jetbrains.kotlin.scripting.definitions.ScriptDefinition]s.
 *
 * Definitions are organized in a sequential list. To locate a definition that matches a script, we need to identify
 * the first one where the [org.jetbrains.kotlin.scripting.definitions.ScriptDefinition.isScript] function returns `true`.
 *
 * The order of definitions is defined by [org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource]s they are discovered and loaded by. Since every source
 * provides its own definitions list, the resulting one is partitioned. Partitions in their turn are sorted.
 * E.g. if definition sources are ordered as (A, B, C), their definitions might look as ((A-def-1, A-def-2), (B-def), (C-def)).
 *
 * Their order is crucial because it affects definition search algo.
 *
 * In rare exceptional cases, the resulting definitions' order might be inaccurate and doesn't accommodate the user's needs.
 * The actual matching definition precedes the one that is desired. As a workaround, all methods and properties exposing definitions consider
 * [KotlinScriptingSettingsImpl] - UI-manageable settings defining "correct" order. Explicit [reorderScriptDefinitions] method exists solely for
 * this purpose.
 *
 * **Note** that the class is `open` for inheritance only for the testing purpose. Its dependencies are cut via a set of `protected open`
 * methods.
 */
open class ScriptDefinitionsManager(private val project: Project) : IdeScriptDefinitionProvider(), Disposable {

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

    /**
     * Property generates a sequence of all discovered definitions.
     * Definitions disabled via [KotlinScriptingSettingsImpl.isScriptDefinitionEnabled] are filtered out.
     * The sequence is ordered according to the [KotlinScriptingSettingsImpl.getScriptDefinitionOrder] or, if the latter is missing,
     * conforms default by-source order (see [ScriptDefinitionsManager]).
     * @see [getDefinitions]
     */
    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val scriptingSettings = getKotlinScriptingSettings()
            return getOrLoadDefinitions().asSequence().filter { scriptingSettings.isScriptDefinitionEnabled(it) }
        }

    /**
     *  Property lists all discovered definitions with no [KotlinScriptingSettingsImpl.isScriptDefinitionEnabled] filtering applied.
     *  If by the moment of the call any of the [ScriptDefinitionsSource]s has not yet contributed to the resulting set of definitions,
     *  it's called before returning the result.
     *  @return All discovered definitions. The list is sorted according to the [KotlinScriptingSettingsImpl.getScriptDefinitionOrder] or,
     *  if the latter is missing, conforms default by-source order (see [ScriptDefinitionsManager]).
     *  @see [currentDefinitions]
     */
    override fun getDefinitions(): List<ScriptDefinition> = getOrLoadDefinitions()

    /**
     * Searches script definition that best matches the specified [script].
     * Contribution from all [ScriptDefinitionsSource]s in taken into configuration. If any of the sources has not yet provided its
     * input by the moment of the method call it's triggered proactively.
     */
    override fun findDefinition(script: SourceCode): ScriptDefinition? {
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

    /**
     * Goes through the list of registered [ScriptDefinitionsSource]s and triggers definitions reload.
     * Result of previous reloads is invalidated including those launched via [reloadDefinitionsBy].
     * @return All discovered definitions. The list is sorted according to the [KotlinScriptingSettingsImpl.getScriptDefinitionOrder] or,
     * if the latter is missing, conforms default by-source order (see [ScriptDefinitionsManager]).
     */
    fun reloadDefinitions(): List<ScriptDefinition> = reloadDefinitionsInternal(getSources())

    /**
     * Reloads definitions from the requested [source] only. Definitions provided by other [ScriptDefinitionsSource]s remain as is
     * (could remain unloaded).
     * @return All definitions known by the moment the method is complete.
     * The list is sorted according to the [KotlinScriptingSettingsImpl.getScriptDefinitionOrder] or, if the latter is missing, conforms
     * default by-source order (see [ScriptDefinitionsManager]).
     */
    fun reloadDefinitionsBy(source: ScriptDefinitionsSource): List<ScriptDefinition> = reloadDefinitionsInternal(listOf(source))

    /**
     * Reorders all known definitions according to the [KotlinScriptingSettingsImpl.getScriptDefinitionOrder].
     *
     * The method is intended for a narrow range of purposes and should not be used in regular production scenarios.
     * Among those purposes are testing, troubleshooting and workaround for the case when some definition is preferred to a desired one.
     *
     *  @return Reordered definitions known by the moment of the method call.
     */
    fun reorderDefinitions(): List<ScriptDefinition> {
        if (definitions == null) return emptyList()
        val scriptingSettings = getKotlinScriptingSettings()

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
        return definitions ?: emptyList()
    }

    /**
     * @return Definition bundled with IDEA and aimed for basic '.kts' scripts support.
     */
    override fun getDefaultDefinition(): ScriptDefinition {
        return project.defaultDefinition
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
        var loadedDefinitions: List<ScriptDefinition>? = null

        val (ms, newDefinitionsBySource) = measureTimeMillisWithResult {
          sources.associateWith {
            val (ms, definitions) = measureTimeMillisWithResult { it.safeGetDefinitions() /* can acquire read-action inside */ }
              scriptingDebugLog { "Loaded definitions: time = $ms ms, source = ${it.javaClass.name}, definitions = ${definitions.map { it.name }}" }
            definitions
          }
        }

        scriptingDebugLog { "Definitions loading total time: $ms ms" }

        val scriptingSettings = getKotlinScriptingSettings()

        withLocks {
            if (definitionsBySource.isEmpty()) {
                // Keeping definition sources' order is a crucial contract.
                // Here we initialize our preserve-insertion-order-map with all known sources-in-desired-order.
                // Values are updated later accordingly.
                getSources().forEach { definitionsBySource[it] = emptyList() }
            }

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
        val fromNewEp = SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
        return fromNewEp.dropLast(1) + fromNewEp.last()
    }

    protected open fun getKotlinScriptingSettings(): KotlinScriptingSettingsImpl = KotlinScriptingSettingsImpl.Companion.getInstance(project)

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

    protected open fun executeUnderReadLock(block: () -> Unit) = runReadAction { block() }

    // FOR TESTS ONLY: END
}