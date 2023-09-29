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
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.PathUtilEx
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.util.writeWithCheckCanceled
import org.jetbrains.kotlin.idea.core.script.configuration.CompositeScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.script.ScriptTemplatesProvider
import org.jetbrains.kotlin.scripting.definitions.*
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.utils.addToStdlib.flattenTo
import java.io.File
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.concurrent.read
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.toScriptSource

internal class LoadScriptDefinitionsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) : Unit = blockingContextScope {
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

    fun reloadDefinitionsBy(source: ScriptDefinitionsSource) {
        definitionsLock.writeWithCheckCanceled {
            if (definitions == null) {
                sourcesToReload.add(source)
                return // not loaded yet
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

    fun reloadScriptDefinitionsIfNeeded() {
        definitions ?: loadScriptDefinitions()
    }

    fun reloadScriptDefinitions() = loadScriptDefinitions()

    private fun loadScriptDefinitions() {
        if (project.isDisposed) return

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
    }

    fun reorderScriptDefinitions() {
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

    fun getAllDefinitions(): List<ScriptDefinition> = definitions ?: run {
        reloadScriptDefinitions()
        definitions!!
    }

    fun isReady(): Boolean {
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

    companion object {
        fun getInstance(project: Project): ScriptDefinitionsManager =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionsManager
    }
}