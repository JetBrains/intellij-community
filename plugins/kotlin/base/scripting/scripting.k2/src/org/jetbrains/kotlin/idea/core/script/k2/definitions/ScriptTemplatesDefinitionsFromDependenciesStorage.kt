// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplatesByPaths
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.shared.definition.ScriptDefinitionMarkerFileType
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

private const val MAIN_KTS = "org.jetbrains.kotlin.mainKts.MainKtsScript.classname"

class ScriptTemplatesFromDependenciesDefinitionSource(
    private val project: Project,
    coroutineScope: CoroutineScope
) : ScriptDefinitionsSource {

    @Volatile
    private var _definitions: List<ScriptDefinition> = emptyList()

    override val definitions: Sequence<ScriptDefinition>
        get() = _definitions.asSequence()

    private var oldTemplates: DiscoveredDefinitionsState? = null

    init {
        val persistedState = ScriptTemplatesDefinitionsFromDependenciesStorage.getInstance(project).state
        coroutineScope.launch { loadDefinitions(persistedState) }
    }

    suspend fun scanAndLoadDefinitions(): List<ScriptDefinition> {
        val newTemplates = readAction {
            val templatesFolders = FilenameIndex.getVirtualFilesByName(ScriptDefinitionMarkerFileType.lastPathSegment, project.allScope())
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val files = mutableListOf<VirtualFile>()
            for (templatesFolder in templatesFolders) {
                val children = templatesFolder?.takeIf { ScriptDefinitionMarkerFileType.isParentOfMyFileType(it) }
                    ?.takeIf { projectFileIndex.isInSource(it) || projectFileIndex.isInLibraryClasses(it) }?.children?.filter {
                        !it.name.endsWith(
                            MAIN_KTS
                        )
                    } ?: continue
                files += children
            }

            getTemplateClassPath(files)
        }

        if (newTemplates == oldTemplates) return emptyList()

        scriptingDebugLog { "Script templates found: $newTemplates" }

        oldTemplates = newTemplates

        ScriptTemplatesDefinitionsFromDependenciesStorage.getInstance(project).loadState(newTemplates)
        return loadDefinitions(newTemplates)
    }

    private fun loadDefinitions(
        newTemplates: DiscoveredDefinitionsState
    ): List<ScriptDefinition> {
        val hostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
            getEnvironment {
                mapOf(
                    "projectRoot" to (project.basePath ?: project.baseDir.canonicalPath)?.let(::File),
                )
            }
        }

        val newDefinitions = loadDefinitionsFromTemplatesByPaths(
            templateClassNames = newTemplates.templates,
            templateClasspath = newTemplates.classpath.map { Path.of(it) },
            baseHostConfiguration = hostConfiguration,
        ).map {
            it.apply { order = Int.MIN_VALUE }
        }

        scriptingDebugLog { "Script definitions found: ${newDefinitions.joinToString()}" }

        _definitions = newDefinitions

        ScriptDefinitionProviderImpl.getInstance(project).notifyDefinitionsChanged()

        return newDefinitions
    }

    private fun getTemplateClassPath(files: Collection<VirtualFile>): DiscoveredDefinitionsState {
        val rootDirToTemplates: MutableMap<VirtualFile, MutableList<VirtualFile>> = hashMapOf()
        for (file in files) { // parent of SCRIPT_DEFINITION_MARKERS_PATH, i.e. of `META-INF/kotlin/script/templates/`
            val dir = file.parent?.parent?.parent?.parent?.parent ?: continue
            rootDirToTemplates.getOrPut(dir) { arrayListOf() }.add(file)
        }

        val templates = linkedSetOf<String>()
        val classpath = linkedSetOf<Path>()

        rootDirToTemplates.forEach { (root, templateFiles) ->
            scriptingDebugLog { "Root matching SCRIPT_DEFINITION_MARKERS_PATH found: ${root.path}" }

            val orderEntriesForFile = ProjectFileIndex.getInstance(project).getOrderEntriesForFile(root).filter {
                if (it is ModuleSourceOrderEntry) {
                    if (ModuleRootManager.getInstance(it.ownerModule).fileIndex.isInTestSourceContent(root)) {
                        return@filter false
                    }

                    it.getFiles(OrderRootType.SOURCES).contains(root)
                } else {
                    it is LibraryOrSdkOrderEntry && it.getRootFiles(OrderRootType.CLASSES).contains(root)
                }
            }.takeIf { it.isNotEmpty() } ?: return@forEach

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

        return DiscoveredDefinitionsState(templates, classpath)
    }
}

@Service(Service.Level.PROJECT)
@State(
    name = "ScriptTemplatesDefinitionsFromDependenciesStorage", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
private class ScriptTemplatesDefinitionsFromDependenciesStorage :
    SimplePersistentStateComponent<DiscoveredDefinitionsState>(DiscoveredDefinitionsState()) {

    companion object {
        fun getInstance(project: Project): ScriptTemplatesDefinitionsFromDependenciesStorage = project.service()
    }
}

internal class DiscoveredDefinitionsState() : BaseState() {
    var templates by list<String>()
    var classpath by list<String>()

    constructor(templates: Collection<String>, classpath: Collection<Path>) : this() {
        this.templates = templates.toMutableList()
        this.classpath = classpath.map { it.absolutePathString() }.toMutableList()
    }
}
