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
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.SLRUMap
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.CheckCanceledLock
import org.jetbrains.kotlin.idea.base.util.writeWithCheckCanceled
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import org.jetbrains.kotlin.scripting.definitions.*
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.toScriptSource

val loadScriptDefinitionsOnDemand
    get() = Registry.`is`("kotlin.scripting.load.definitions.on.demand", true)

internal class LoadScriptDefinitionsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) : Unit = blockingContextScope {
        if (loadScriptDefinitionsOnDemand) return@blockingContextScope

        if (isUnitTestMode()) {
            // In tests definitions are loaded synchronously because they are needed to analyze script
            // In IDE script won't be highlighted before all definitions are loaded, then the highlighting will be restarted
            ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
        } else {
            ScriptDefinitionsManager.getInstance(project).reloadScriptDefinitionsIfNeeded()
            ScriptConfigurationManager.getInstance(project).loadPlugins()
        }
    }
}

class ScriptDefinitionsManager(private val project: Project) : LazyScriptDefinitionProvider(), Disposable {

    private val delegate = if (loadScriptDefinitionsOnDemand) NewLogicDelegate(project) else OldLogicDelegate(project)

    override val currentDefinitions: Sequence<ScriptDefinition>
        get() = delegate.currentDefinitions


    override fun findDefinition(script: SourceCode): ScriptDefinition? = delegate.findDefinition(script)

    @Deprecated("Migrating to configuration refinement", level = DeprecationLevel.ERROR)
    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? = delegate.findScriptDefinition(fileName)

    fun getAllDefinitions(): List<ScriptDefinition> = delegate.getAllDefinitions()

    fun reloadScriptDefinitionsIfNeeded() = delegate.reloadScriptDefinitionsIfNeeded()

    fun reloadScriptDefinitions() = delegate.reloadScriptDefinitions()

    fun isReady(): Boolean = delegate.isReady()

    fun reloadDefinitionsBy(source: ScriptDefinitionsSource): List<ScriptDefinition> = delegate.reloadDefinitionsBy(source)

    fun reorderScriptDefinitions() = delegate.reorderScriptDefinitions()

    override fun getDefaultDefinition(): ScriptDefinition = delegate.getDefaultDefinition()

    override fun dispose() = Disposer.dispose(delegate)

    companion object {
        fun getInstance(project: Project): ScriptDefinitionsManager =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionsManager
    }
}

abstract class LogicDelegate : LazyScriptDefinitionProvider(), Disposable {

    public override val currentDefinitions: Sequence<ScriptDefinition>
        get() = error("subclass implementation is required")

    override fun dispose() {}

    abstract fun reloadScriptDefinitionsIfNeeded(): List<ScriptDefinition>

    abstract fun reloadScriptDefinitions(): List<ScriptDefinition>

    abstract fun isReady(): Boolean

    abstract fun getAllDefinitions(): List<ScriptDefinition>

    abstract fun reorderScriptDefinitions()

    abstract fun reloadDefinitionsBy(source: ScriptDefinitionsSource): List<ScriptDefinition>
}

class OldLogicDelegate(private val project: Project) : LogicDelegate() {

    private val definitionsLock = ReentrantReadWriteLock()
    private val definitionsBySource = mutableMapOf<ScriptDefinitionsSource, List<ScriptDefinition>>()

    @Volatile
    private var definitions: List<ScriptDefinition>? = null
    private val sourcesToReload = mutableSetOf<ScriptDefinitionsSource>()

    private val failedContributorsHashes = HashSet<Int>()

    private val scriptDefinitionsCacheLock = CheckCanceledLock()
    private val scriptDefinitionsCache = SLRUMap<String, ScriptDefinition>(10, 10)

    // cache service as it's getter is on the hot path
    // it is safe, since both services are in same plugin
    @Volatile
    private var configurations: CompositeScriptConfigurationManager? =
        ScriptConfigurationManager.compositeScriptConfigurationManager(project)

    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null

        configurations?.tryGetScriptDefinitionFast(locationId)?.let { fastPath -> return fastPath }
        scriptDefinitionsCacheLock.withLock { scriptDefinitionsCache.get(locationId) }?.let { cached -> return cached }

        val definition =
            if (isScratchFile(script)) {
                // Scratch should always have the default script definition
                getDefaultDefinition()
            } else {
                if (definitions == null) return DeferredScriptDefinition(script, this)
                super.findDefinition(script) // Some embedded scripts (e.g., Kotlin Notebooks) have their own definition
                    ?: if (isEmbeddedScript(script)) getDefaultDefinition() else return null
            }

        scriptDefinitionsCacheLock.withLock {
            scriptDefinitionsCache.put(locationId, definition)
        }

        return definition
    }

    internal fun DeferredScriptDefinition.valueIfAny(): ScriptDefinition? {
        if (definitions == null) return null

        val locationId = requireNotNull(scriptCode.locationId)
        configurations?.tryGetScriptDefinitionFast(locationId)?.let { fastPath -> return fastPath }
        scriptDefinitionsCacheLock.withLock { scriptDefinitionsCache.get(locationId) }?.let { cached -> return cached }
        return super.findDefinition(scriptCode)
    }

    private fun isScratchFile(script: SourceCode): Boolean {
        val virtualFile =
            if (script is VirtualFileScriptSource) script.virtualFile
            else script.locationId?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        return virtualFile != null && ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType
    }

    private fun isEmbeddedScript(code: SourceCode): Boolean {
        val scriptSource = code as? VirtualFileScriptSource ?: return false
        val virtualFile = scriptSource.virtualFile
        return virtualFile is VirtualFileWindow && virtualFile.fileType == KotlinFileType.INSTANCE
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? {
        @Suppress("DEPRECATION")
        return findDefinition(File(fileName).toScriptSource())?.legacyDefinition
    }

    override fun reloadDefinitionsBy(source: ScriptDefinitionsSource): List<ScriptDefinition> {
        definitionsLock.writeWithCheckCanceled {
            if (definitions == null) {
                sourcesToReload.add(source)
                return emptyList() // not loaded yet
            }
            if (source !in definitionsBySource) error("Unknown script definition source: $source")
        }

        val safeGetDefinitions = source.safeGetDefinitions()
        val updateDefinitionsResult = run {
            definitionsLock.writeWithCheckCanceled {
                definitionsBySource[source] = safeGetDefinitions
                definitions = definitionsBySource.values.flattenTo(mutableListOf())
            }
            updateDefinitions()
        }
        updateDefinitionsResult?.apply()
        return definitions ?: emptyList()
    }

    override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val scriptingSettings = kotlinScriptingSettingsSafe() ?: return emptySequence()
            return (definitions ?: run {
                reloadScriptDefinitions()
                definitions!!
            }).asSequence().filter { scriptingSettings.isScriptDefinitionEnabled(it) }
        }

    private fun getSources(): List<ScriptDefinitionsSource> {
        @Suppress("DEPRECATION")
        val fromDeprecatedEP = project.extensionArea.getExtensionPoint(ScriptTemplatesProvider.EP_NAME).extensions.toList()
            .map { ScriptTemplatesProviderAdapter(it).asSource() }
        val fromNewEp = ScriptDefinitionContributor.EP_NAME.getPoint(project).extensions.toList()
            .map { it.asSource() }
        return fromNewEp.dropLast(1) + fromDeprecatedEP + fromNewEp.last()
    }

    override fun reloadScriptDefinitionsIfNeeded(): List<ScriptDefinition> {
        return definitions ?: loadScriptDefinitions()
    }

    override fun reloadScriptDefinitions(): List<ScriptDefinition> = loadScriptDefinitions()

    private fun loadScriptDefinitions(): List<ScriptDefinition> {
        if (project.isDisposed) return emptyList()

        val newDefinitionsBySource = getSources().associateWith { it.safeGetDefinitions() }

        val updateDefinitionsResult = run {
            definitionsLock.writeWithCheckCanceled {
                definitionsBySource.putAll(newDefinitionsBySource)
                definitions = definitionsBySource.values.flattenTo(mutableListOf())
            }
            updateDefinitions()
        }
        updateDefinitionsResult?.apply()

        definitionsLock.writeWithCheckCanceled {
            sourcesToReload.takeIf { it.isNotEmpty() }?.let {
                val copy = ArrayList<ScriptDefinitionsSource>(it)
                it.clear()
                copy
            }
        }?.forEach(::reloadDefinitionsBy)

        return definitions ?: emptyList()
    }

    override fun reorderScriptDefinitions() {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: return
        val updateDefinitionsResult = run {
            definitionsLock.writeWithCheckCanceled {
                definitions?.let { list ->
                    list.forEach {
                        it.order = scriptingSettings.getScriptDefinitionOrder(it)
                    }
                    definitions = list.sortedBy(ScriptDefinition::order)
                }
            }
            updateDefinitions()
        }
        updateDefinitionsResult?.apply()
    }

    private fun kotlinScriptingSettingsSafe(): KotlinScriptingSettings? {
        assert(!definitionsLock.isWriteLockedByCurrentThread) { "kotlinScriptingSettingsSafe should be called out if the write lock to avoid deadlocks" }
        return runReadAction {
            if (!project.isDisposed) KotlinScriptingSettings.getInstance(project) else null
        }
    }

    override fun getAllDefinitions(): List<ScriptDefinition> = definitions ?: run {
        reloadScriptDefinitions()
        definitions!!
    }

    override fun isReady(): Boolean {
        if (definitions == null) return false
        val keys = definitionsLock.writeWithCheckCanceled { definitionsBySource.keys }
        return keys.all { source ->
            // TODO: implement another API for readiness checking
            (source as? ScriptDefinitionContributor)?.isReady() != false
        }
    }

    override fun getDefaultDefinition(): ScriptDefinition {
        val bundledScriptDefinitionContributor = ScriptDefinitionContributor.find<BundledScriptDefinitionContributor>(project)
            ?: error("StandardScriptDefinitionContributor should be registered in plugin.xml")
        return ScriptDefinition.FromLegacy(getScriptingHostConfiguration(), bundledScriptDefinitionContributor.getDefinitions().last())
    }

    private fun updateDefinitions(): UpdateDefinitionsResult? {
        assert(!definitionsLock.isWriteLockedByCurrentThread) { "updateDefinitions should be called out the write lock" }
        if (project.isDisposed) return null

        val fileTypeManager = FileTypeManager.getInstance()

        val newExtensions = getKnownFilenameExtensions().toSet().filter {
            val fileTypeByExtension = fileTypeManager.getFileTypeByFileName("xxx.$it")
            val notKnown = fileTypeByExtension != KotlinFileType.INSTANCE
            if (notKnown) {
                scriptingWarnLog("extension $it file type [${fileTypeByExtension.name}] is not registered as ${KotlinFileType.INSTANCE.name}")
            }
            notKnown
        }.toSet()

        clearCache()
        scriptDefinitionsCacheLock.withLock { scriptDefinitionsCache.clear() }

        return UpdateDefinitionsResult(project, newExtensions)
    }

    private data class UpdateDefinitionsResult(val project: Project, val newExtensions: Set<String>) {
        fun apply() {
            if (newExtensions.isNotEmpty()) {
                scriptingWarnLog("extensions ${newExtensions} is about to be registered as ${KotlinFileType.INSTANCE.name}")
                // Register new file extensions
                ApplicationManager.getApplication().invokeLater {
                    val fileTypeManager = FileTypeManager.getInstance()
                    runWriteAction {
                        newExtensions.forEach {
                            fileTypeManager.associateExtension(KotlinFileType.INSTANCE, it)
                        }
                    }
                }
            }

            // TODO: clear by script type/definition
            ScriptConfigurationManager.getInstance(project).updateScriptDefinitionReferences()
        }
    }

    private fun ScriptDefinitionsSource.safeGetDefinitions(): List<ScriptDefinition> {
        if (!failedContributorsHashes.contains(this@safeGetDefinitions.hashCode())) try {
            return definitions.toList()
        } catch (t: Throwable) {
            if (t is ControlFlowException) throw t
            // reporting failed loading only once
            failedContributorsHashes.add(this@safeGetDefinitions.hashCode())
            scriptingErrorLog("Cannot load script definitions from $this: ${t.cause?.message ?: t.message}", t)
        }
        return emptyList()
    }

    override fun dispose() {
        super.dispose()

        clearCache()

        definitionsBySource.clear()
        definitions = null
        failedContributorsHashes.clear()
        scriptDefinitionsCache.clear()
        configurations = null
    }
}


class NewLogicDelegate(private val project: Project) : LogicDelegate() {

    private val definitionsLock = ReentrantLock()

    // @GuardedBy("definitionsLock")
    // Support for insertion order is crucial because 'getSources()' is based on EP order in XML (default configuration source goes last)
    private val definitionsBySource = mutableMapOf<ScriptDefinitionsSource, List<ScriptDefinition>>()

    @Volatile
    private var definitions: List<ScriptDefinition>? = null

    private val activatedDefinitionSources: MutableSet<ScriptDefinitionsSource> = ConcurrentHashMap.newKeySet()

    private val failedContributorsHashes: MutableSet<Int> = ConcurrentHashMap.newKeySet()


    // cache service as it's getter is on the hot path
    // it is safe, since both services are in same plugin
    @Volatile
    private var configurations: CompositeScriptConfigurationManager? =
        ScriptConfigurationManager.compositeScriptConfigurationManager(project)

    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null

        configurations?.tryGetScriptDefinitionFast(locationId)?.let { fastPath -> return fastPath }

        getOrLoadDefinitions()

        val definition =
            if (isScratchFile(script)) {
                // Scratch should always have the default script definition
                getDefaultDefinition()
            } else {
                super.findDefinition(script) // Some embedded scripts (e.g., Kotlin Notebooks) have their own definition
                    ?: if (isEmbeddedScript(script)) getDefaultDefinition() else return null
            }

        return definition
    }


    private fun isScratchFile(script: SourceCode): Boolean {
        val virtualFile =
            if (script is VirtualFileScriptSource) script.virtualFile
            else script.locationId?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        return virtualFile != null && ScratchFileService.getInstance().getRootType(virtualFile) is ScratchRootType
    }

    private fun isEmbeddedScript(code: SourceCode): Boolean {
        val scriptSource = code as? VirtualFileScriptSource ?: return false
        val virtualFile = scriptSource.virtualFile
        return virtualFile is VirtualFileWindow && virtualFile.fileType == KotlinFileType.INSTANCE
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun findScriptDefinition(fileName: String): KotlinScriptDefinition? {
        @Suppress("DEPRECATION")
        return findDefinition(File(fileName).toScriptSource())?.legacyDefinition
    }

    override fun reloadDefinitionsBy(source: ScriptDefinitionsSource) = reloadDefinitionsInternal(listOf(source))

    private fun reloadDefinitionsInternal(sources: List<ScriptDefinitionsSource>): List<ScriptDefinition> {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: error("Kotlin script setting not found")

        var loadedDefinitions: List<ScriptDefinition>? = null

        definitionsLock.withLock {
            val (ms, newDefinitionsBySource) = measureTimeMillisWithResult {
                sources.associateWith {
                    val (ms, definitions) = measureTimeMillisWithResult { it.safeGetDefinitions() }
                    scriptingDebugLog { "Loaded definitions: time = $ms ms, source = ${it.javaClass.name}, definitions = ${definitions.map { it.name }}" }
                    definitions
                }
            }

            scriptingDebugLog { "Definitions loading total time: $ms ms" }

            if (newDefinitionsBySource.isEmpty()) return emptyList()

            definitionsBySource.putAll(newDefinitionsBySource)

            loadedDefinitions = definitionsBySource.values.flattenTo(mutableListOf())
                .onEach { it.order = scriptingSettings.getScriptDefinitionOrder(it) }
                .sortedBy(ScriptDefinition::order)
                .takeIf { it.isNotEmpty() }

            definitions = loadedDefinitions
        }

        activatedDefinitionSources.addAll(sources)

        applyDefinitionsUpdate() // <== acquires read-action inside

        return loadedDefinitions ?: emptyList()
    }

    override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val scriptingSettings = kotlinScriptingSettingsSafe() ?: return emptySequence()
            return getOrLoadDefinitions().asSequence().filter { scriptingSettings.isScriptDefinitionEnabled(it) }
        }

    private fun allDefinitionSourcesContributedToCache(): Boolean = activatedDefinitionSources.containsAll(getSources())

    private fun getSources(): List<ScriptDefinitionsSource> {
        @Suppress("DEPRECATION")
        val fromDeprecatedEP = project.extensionArea.getExtensionPoint(ScriptTemplatesProvider.EP_NAME).extensions.toList()
            .map { ScriptTemplatesProviderAdapter(it).asSource() }
        val fromNewEp = ScriptDefinitionContributor.EP_NAME.getPoint(project).extensions.toList()
            .map { it.asSource() }
        return fromNewEp.dropLast(1) + fromDeprecatedEP + fromNewEp.last()
    }

    private fun getOrLoadDefinitions(): List<ScriptDefinition> {
        // This is not thread safe, but if the condition changes by the time of the "then do this" it's ok - we just refresh the data.
        // Taking local lock here is dangerous due to the possible global read-lock acquisition (hence, the deadlock). See KTIJ-27838.
        return if (definitions == null || !allDefinitionSourcesContributedToCache()) {
            reloadDefinitionsInternal(getSources())
        } else {
            definitions ?: error("'definitions' became null after they weren't")
        }
    }

    override fun reloadScriptDefinitionsIfNeeded(): List<ScriptDefinition> = getOrLoadDefinitions()

    override fun reloadScriptDefinitions(): List<ScriptDefinition> = reloadDefinitionsInternal(getSources())

    override fun reorderScriptDefinitions() {
        val scriptingSettings = kotlinScriptingSettingsSafe() ?: return
        if (definitions == null) return

        definitionsLock.withLock {
            definitions?.let { list ->
                list.forEach {
                    it.order = scriptingSettings.getScriptDefinitionOrder(it)
                }
                definitions = list.sortedBy(ScriptDefinition::order)
            }
        }

        applyDefinitionsUpdate()  // <== acquires read-action inside
    }

    private fun kotlinScriptingSettingsSafe(): KotlinScriptingSettings? {
        return runReadAction {
            if (!project.isDisposed) KotlinScriptingSettings.getInstance(project) else null
        }
    }

    override fun getAllDefinitions(): List<ScriptDefinition> = getOrLoadDefinitions()

    override fun isReady(): Boolean = true

    override fun getDefaultDefinition(): ScriptDefinition {
        val bundledScriptDefinitionContributor = ScriptDefinitionContributor.find<BundledScriptDefinitionContributor>(project)
            ?: error("StandardScriptDefinitionContributor should be registered in plugin.xml")
        return ScriptDefinition.FromLegacy(getScriptingHostConfiguration(), bundledScriptDefinitionContributor.getDefinitions().last())
    }

    private fun applyDefinitionsUpdate() {
        associateFileExtensionsIfNeeded()
        ScriptConfigurationManager.getInstance(project).updateScriptDefinitionReferences()
    }

    private fun associateFileExtensionsIfNeeded() {
        if (project.isDisposed) return

        clearCache()

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

    override fun dispose() {
        super.dispose()

        clearCache()

        definitionsBySource.clear()
        definitions = null
        activatedDefinitionSources.clear()
        failedContributorsHashes.clear()
        configurations = null
    }
}

