// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.core.script.shared.definition.ScriptDefinitionMarkerFileType
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import java.nio.file.Path
import kotlin.io.path.absolutePathString

private const val MAIN_KTS = "org.jetbrains.kotlin.mainKts.MainKtsScript.classname"

data class DefinitionTemplates(
    val fqns: List<String>,
    val classpath: List<String>
)

@Service(Service.Level.PROJECT)
class DefinitionFromDependenciesContributor(private val project: Project) {
    suspend fun discoverDefinitionTemplates(): DefinitionTemplates {
        return readAction {
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
        }.also {
            scriptingDebugLog { "Script templates found: ${it.fqns}" }
        }
    }

    suspend fun updateWorkspaceModel(templates: DefinitionTemplates) {
        project.workspaceModel.update("refreshing discovered script templates from project dependencies") { storage ->
            val existing = storage.entities(ScriptDefinitionTemplateEntity::class.java).singleOrNull()
            if (existing != null) {
                storage.modifyScriptDefinitionTemplateEntity(existing) {
                    this.templateFqns = templates.fqns.toMutableList()
                    this.classpath = templates.classpath.toMutableList()
                }
            } else {
                storage addEntity ScriptDefinitionTemplateEntity(
                    templateFqns = templates.fqns,
                    classpath = templates.classpath,
                    entitySource = DefinitionFromDependenciesEntitySource,
                )
            }
        }
    }

    private fun getTemplateClassPath(files: Collection<VirtualFile>): DefinitionTemplates {
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

        return DefinitionTemplates(templates.toList(),classpath.map { it.absolutePathString() })
    }
}

internal class ScriptDefinitionTemplateListener(val project: Project) : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
        if (event.getChanges(ScriptDefinitionTemplateEntity::class.java).any()) {
            ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
        }
    }
}