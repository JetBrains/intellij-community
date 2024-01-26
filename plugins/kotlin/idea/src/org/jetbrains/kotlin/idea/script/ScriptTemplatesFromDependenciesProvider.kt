// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.script

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionSourceAsContributor
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.loadScriptDefinitionsOnDemand
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

private val logger = Logger.getInstance(ScriptTemplatesFromDependenciesProvider::class.java)

class ScriptTemplatesFromDependenciesProvider(private val project: Project) : ScriptDefinitionSourceAsContributor {

    @Deprecated("migrating to new configuration refinement: drop usages")
    override val id = "ScriptTemplatesFromDependenciesProvider"

    @Deprecated("migrating to new configuration refinement: drop usages")
    override fun isReady(): Boolean = _definitions != null

    override val definitions: Sequence<ScriptDefinition>
        get() {
            definitionsLock.withLock {
                _definitions?.let { return it.asSequence() }
            }

            forceStartUpdate = false
            asyncRunUpdateScriptTemplates()
            return _definitions?.asSequence() ?: emptySequence()
        }

    init {
        val disposable = KotlinPluginDisposable.getInstance(project)
        val connection = project.messageBus.connect(disposable)
        connection.subscribe(FileTypeIndex.INDEX_CHANGE_TOPIC, FileTypeIndex.IndexChangeListener { fileType ->
            if (fileType == ScriptDefinitionMarkerFileType && project.isInitialized) {
                forceStartUpdate = true
                asyncRunUpdateScriptTemplates(isFileIndexUpdate = true)
            }
        })
    }

    private fun asyncRunUpdateScriptTemplates(isFileIndexUpdate: Boolean = false) {
        definitionsLock.withLock {
            if (!forceStartUpdate && _definitions != null) return
        }

        if (inProgress.compareAndSet(false, true)) {
            loadScriptDefinitions(isFileIndexUpdate)
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

    @RequiresBlockingContext
    private fun loadScriptDefinitions(isFileIndexUpdate: Boolean) {
        if (project.isDefault || project.isDisposed) {
            return onEarlyEnd()
        }

        if (logger.isDebugEnabled) {
            logger.debug("async script definitions update started")
        }

        if (loadScriptDefinitionsOnDemand && !isFileIndexUpdate) {
            loadSync(EmptyProgressIndicator())
        } else {
            val task = object : Task.Backgroundable(
                project, KotlinBundle.message("kotlin.script.lookup.definitions"), false
            ) {
                override fun run(indicator: ProgressIndicator) {
                    loadSync(indicator)
                }
            }

            ProgressManager.getInstance().runProcessWithProgressAsynchronously(
                task,
                BackgroundableProcessIndicator(task)
            )
        }
    }


    private fun loadSync(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
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
                getTemplateClassPath(files, indicator)
            })
                .expireWith(pluginDisposable)
                .wrapProgress(indicator)
                .executeSynchronously() ?: return onEarlyEnd()
        try {
            indicator.checkCanceled()
            if (pluginDisposable.disposed || !inProgress.get() || templates.isEmpty()) return onEarlyEnd()

            val newTemplates = TemplatesWithCp(templates.toList(), classpath.toList())
            if (!inProgress.get() || newTemplates == oldTemplates) return onEarlyEnd()

            if (logger.isDebugEnabled) {
                logger.debug("script templates found: $newTemplates")
            }

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
            indicator.checkCanceled()
            if (logger.isDebugEnabled) {
                logger.debug("script definitions found: ${newDefinitions.joinToString()}")
            }

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
    fun getTemplateClassPath(files: Collection<VirtualFile>, indicator: ProgressIndicator): Pair<Collection<String>, Collection<Path>> {
        val rootDirToTemplates: MutableMap<VirtualFile, MutableList<VirtualFile>> = hashMapOf()
        for (file in files) {
            // parent of SCRIPT_DEFINITION_MARKERS_PATH, i.e. of `META-INF/kotlin/script/templates/`
            val dir = file.parent?.parent?.parent?.parent?.parent ?: continue
            rootDirToTemplates.getOrPut(dir) { arrayListOf() }.add(file)
        }

        val templates = linkedSetOf<String>()
        val classpath = linkedSetOf<Path>()

        rootDirToTemplates.forEach { (root, templateFiles) ->
            if (logger.isDebugEnabled) {
                logger.debug("root matching SCRIPT_DEFINITION_MARKERS_PATH found: ${root.path}")
            }

            val orderEntriesForFile = ProjectFileIndex.getInstance(project).getOrderEntriesForFile(root)
                .filter {
                    indicator.checkCanceled()
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
                    indicator.checkCanceled()
                    val localVirtualFile = VfsUtil.getLocalFile(virtualFile)
                    localVirtualFile.fileSystem.getNioPath(localVirtualFile)?.let(classpath::add)
                }
            }
        }

        return templates to classpath
    }
}