// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.shared.definition.ScriptDefinitionMarkerFileType
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class ScriptTemplatesFromDependenciesProvider(private val project: Project) : ScriptDefinitionsSource {
    companion object {
        fun getInstance(project: Project): ScriptTemplatesFromDependenciesProvider? =
            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
                .filterIsInstance<ScriptTemplatesFromDependenciesProvider>()
                .singleOrNull()
    }

    override val definitions: Sequence<ScriptDefinition>
        get() {
            definitionsLock.withLock {
                _definitions?.let { return it.asSequence() }
            }

            forceStartUpdate = false
            runUpdateScriptTemplates()
            return _definitions?.asSequence() ?: emptySequence()
        }

    init {
        val disposable = KotlinPluginDisposable.getInstance(project)
        val connection = project.messageBus.connect(disposable)
        connection.subscribe(FileTypeIndex.INDEX_CHANGE_TOPIC, FileTypeIndex.IndexChangeListener { fileType ->
            if (fileType == ScriptDefinitionMarkerFileType && project.isInitialized) {
                forceStartUpdate = true
                runUpdateScriptTemplates()
            }
        })
    }

    private fun runUpdateScriptTemplates() {
        definitionsLock.withLock {
            if (!forceStartUpdate && _definitions != null) return
        }

        if (inProgress.compareAndSet(false, true)) {
            loadScriptDefinitions()
        }
    }

    @Volatile
    private var _definitions: List<ScriptDefinition>? = null
    private val definitionsLock = ReentrantLock()

    private var oldTemplates: TemplatesWithCp? = null

    private data class TemplatesWithCp(
        val templates: List<String>,
        val classpath: List<Path>,
    )

    private val inProgress = AtomicBoolean(false)

    @Volatile
    private var forceStartUpdate = false

    private fun loadScriptDefinitions() {
        if (project.isDefault || project.isDisposed || !project.isInitialized) {
            return onEarlyEnd()
        }

        loadSync()
    }

    private fun loadSync() {
        val pluginDisposable = KotlinPluginDisposable.getInstance(project)
        val (templates, classpath) =
            ReadAction.nonBlocking(Callable {
                val templatesFolders = FilenameIndex.getVirtualFilesByName(ScriptDefinitionMarkerFileType.lastPathSegment, project.allScope())
                val projectFileIndex = ProjectFileIndex.getInstance(project)
                val files = mutableListOf<VirtualFile>()
                for (templatesFolder in templatesFolders) {
                    val children =
                        templatesFolder?.takeIf { ScriptDefinitionMarkerFileType.isParentOfMyFileType(it) }
                            ?.takeIf { projectFileIndex.isInSource(it) || projectFileIndex.isInLibraryClasses(it) }
                            ?.children ?: continue
                    files += children
                }
                getTemplateClassPath(files)
            })
                .expireWith(pluginDisposable)
                .executeSynchronously() ?: return onEarlyEnd()
        try {
            if (pluginDisposable.disposed || !inProgress.get() || templates.isEmpty()) return onEarlyEnd()

            val newTemplates = TemplatesWithCp(templates.toList(), classpath.toList())
            if (!inProgress.get() || newTemplates == oldTemplates) return onEarlyEnd()

            scriptingDebugLog { "Script templates found: $newTemplates" }

            oldTemplates = newTemplates

            val hostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                getEnvironment {
                    mapOf(
                        "projectRoot" to (project.basePath ?: project.baseDir.canonicalPath)?.let(::File),
                    )
                }
            }

            val newDefinitions = loadDefinitionsFromTemplatesByPaths(
                templateClassNames = newTemplates.templates,
                templateClasspath = newTemplates.classpath,
                baseHostConfiguration = hostConfiguration,
            )

            scriptingDebugLog { "Script definitions found: ${newDefinitions.joinToString()}" }

            val needReload = definitionsLock.withLock {
                if (newDefinitions != _definitions) {
                    _definitions = newDefinitions
                    return@withLock true
                }
                return@withLock false
            }

            if (needReload) {
                ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this@ScriptTemplatesFromDependenciesProvider)
            }
        } finally {
            inProgress.set(false)
        }
    }

    private fun onEarlyEnd() {
        definitionsLock.withLock {
            _definitions = emptyList()
        }
        inProgress.set(false)
    }

    // public for tests
    fun getTemplateClassPath(files: Collection<VirtualFile>): Pair<Collection<String>, Collection<Path>> {
        val rootDirToTemplates: MutableMap<VirtualFile, MutableList<VirtualFile>> = hashMapOf()
        for (file in files) {
            // parent of SCRIPT_DEFINITION_MARKERS_PATH, i.e. of `META-INF/kotlin/script/templates/`
            val dir = file.parent?.parent?.parent?.parent?.parent ?: continue
            rootDirToTemplates.getOrPut(dir) { arrayListOf() }.add(file)
        }

        val templates = linkedSetOf<String>()
        val classpath = linkedSetOf<Path>()

        rootDirToTemplates.forEach { (root, templateFiles) ->
            scriptingDebugLog { "Root matching SCRIPT_DEFINITION_MARKERS_PATH found: ${root.path}" }

            val orderEntriesForFile = ProjectFileIndex.getInstance(project).getOrderEntriesForFile(root)
                .filter {
                    if (it is ModuleSourceOrderEntry) {
                        if (ModuleRootManager.getInstance(it.ownerModule).fileIndex.isInTestSourceContent(root)) {
                            return@filter false
                        }

                        it.getFiles(OrderRootType.SOURCES).contains(root)
                    } else {
                        it is LibraryOrSdkOrderEntry && it.getRootFiles(OrderRootType.CLASSES).contains(root)
                    }
                }
                .takeIf { it.isNotEmpty() } ?: return@forEach

            for (virtualFile in templateFiles) {
                templates.add(virtualFile.name.removeSuffix(SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT))
            }

            // assuming that all libraries are placed into classes roots
            // TODO: extract exact library dependencies instead of putting all module dependencies into classpath
            // minimizing the classpath needed to use the template by taking cp only from modules with new templates found
            // on the other hand the approach may fail if some module contains a template without proper classpath, while
            // the other has properly configured classpath, so assuming that the dependencies are set correctly everywhere
            for (orderEntry in orderEntriesForFile) {
                for (virtualFile in OrderEnumerator.orderEntries(orderEntry.ownerModule).withoutSdk().classesRoots) {
                    val localVirtualFile = VfsUtil.getLocalFile(virtualFile)
                    localVirtualFile.fileSystem.getNioPath(localVirtualFile)?.let(classpath::add)
                }
            }
        }

        return templates to classpath
    }
}