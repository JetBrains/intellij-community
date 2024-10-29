// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.script.k2

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.core.script.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.script.ScriptDefinitionMarkerFileType
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

private const val MAIN_KTS = "org.jetbrains.kotlin.mainKts.MainKtsScript.classname"

class ScriptTemplatesFromDependenciesDefinitionSource(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : ScriptDefinitionsSource {

    @Volatile
    private var _definitions: List<ScriptDefinition>? = null

    override val definitions: Sequence<ScriptDefinition>
        get() {
            return _definitions?.asSequence() ?: emptySequence()
        }

    private var oldTemplates: TemplatesWithCp? = null

    init {
        val disposable = KotlinPluginDisposable.getInstance(project)
        val connection = project.messageBus.connect(disposable)
        connection.subscribe(FileTypeIndex.INDEX_CHANGE_TOPIC, FileTypeIndex.IndexChangeListener { fileType ->
            if (fileType == ScriptDefinitionMarkerFileType && project.isInitialized) {
                coroutineScope.launch { scanAndLoadDefinitions() }
            }
        })
    }

    suspend fun scanAndLoadDefinitions(): List<ScriptDefinition> {
        val (templates, classpath) = readAction {
            val templatesFolders =
                FilenameIndex.getVirtualFilesByName(ScriptDefinitionMarkerFileType.lastPathSegment, project.allScope())
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val files = mutableListOf<VirtualFile>()
            for (templatesFolder in templatesFolders) {
                val children =
                    templatesFolder?.takeIf { ScriptDefinitionMarkerFileType.isParentOfMyFileType(it) }
                        ?.takeIf { projectFileIndex.isInSource(it) || projectFileIndex.isInLibraryClasses(it) }
                        ?.children?.filter { !it.name.endsWith(MAIN_KTS) } ?: continue
                files += children
            }
            getTemplateClassPath(files)
        }

        val newTemplates = TemplatesWithCp(templates.toList(), classpath.toList())
        if (newTemplates == oldTemplates) return emptyList()

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
        ).map {
            it.apply { order = Int.MIN_VALUE }
        }

        scriptingDebugLog { "Script definitions found: ${_definitions?.joinToString()}" }

        _definitions = newDefinitions

        K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()

        return newDefinitions
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

    private data class TemplatesWithCp(
        val templates: List<String>,
        val classpath: List<Path>,
    )

    companion object {
        fun getInstance(project: Project): ScriptTemplatesFromDependenciesDefinitionSource? =
            SCRIPT_DEFINITIONS_SOURCES.getExtensions(project)
                .filterIsInstance<ScriptTemplatesFromDependenciesDefinitionSource>().firstOrNull()
                .safeAs<ScriptTemplatesFromDependenciesDefinitionSource>()
    }

}