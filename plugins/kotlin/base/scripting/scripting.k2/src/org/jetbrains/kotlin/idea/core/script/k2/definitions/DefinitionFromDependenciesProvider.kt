// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.caches.project.cacheByClass
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionSettingsStateComponent
import org.jetbrains.kotlin.idea.core.script.k2.settings.parsedClassNames
import org.jetbrains.kotlin.idea.core.script.k2.settings.parsedClasspath
import org.jetbrains.kotlin.idea.core.script.shared.definition.ScriptDefinitionMarkerFileType
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.SCRIPT_DEFINITION_MARKERS_EXTENSION_WITH_DOT
import org.jetbrains.kotlin.utils.addIfNotNull
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

data class DefinitionTemplates(
    val fqns: List<String>,
    val classpath: List<String>
)

private const val MAIN_KTS = "org.jetbrains.kotlin.mainKts.MainKtsScript.classname"

@Suppress("IO_FILE_USAGE")
class DefinitionFromDependenciesProvider(val project: Project) : ScriptDefinitionsProvider {
    override val id: String
        get() = this::class.java.name

    override fun getDefinitionClasses(): Iterable<String> {
        val explicitFqns = ScriptDefinitionSettingsStateComponent.getInstance(project).state.parsedClassNames
        return (explicitFqns + ScriptTemplatesFromDependenciesCache.getOrDiscover(project).fqns).distinct()
    }

    override fun getDefinitionsClassPath(): Iterable<File> {
        val settings = ScriptDefinitionSettingsStateComponent.getInstance(project).state
        val explicitClasspath = settings.parsedClasspath
        val discoveredClasspath = ScriptTemplatesFromDependenciesCache.getOrDiscover(project).classpath
        val autoResolvedClasspath = tryToGuessClasspath(settings.parsedClassNames)
        return (explicitClasspath + discoveredClasspath + autoResolvedClasspath).distinct().map { File(it) }
    }

    override fun useDiscovery(): Boolean = false

    private fun tryToGuessClasspath(fqns: List<String>): List<String> {
        if (fqns.isEmpty()) return emptyList()
        return runReadActionBlocking {
            val javaFacade = JavaPsiFacade.getInstance(project)
            val projectScope = GlobalSearchScope.projectScope(project)
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val classpath = linkedSetOf<Path>()
            for (fqn in fqns) {
                val psiClass = javaFacade.findClass(fqn, projectScope) ?: continue
                val classFile = psiClass.containingFile?.virtualFile ?: continue
                val module = projectFileIndex.getModuleForFile(classFile) ?: continue
                for (virtualFile in OrderEnumerator.orderEntries(module).withoutSdk().classesRoots) {
                    val localVirtualFile = VfsUtil.getLocalFile(virtualFile)
                    classpath.addIfNotNull(localVirtualFile.fileSystem.getNioPath(localVirtualFile))
                }
            }
            classpath.map { it.absolutePathString() }
        }
    }
}

internal object ScriptTemplatesFromDependenciesCache {
    fun getOrDiscover(project: Project): DefinitionTemplates = project.cacheByClass(
        ScriptTemplatesFromDependenciesCache::class.java,
        ScriptDefinitionsModificationTracker.getInstance(project),
    ) {
        runReadActionBlocking {
            val templatesFolders =
                FilenameIndex.getVirtualFilesByName(ScriptDefinitionMarkerFileType.lastPathSegment, project.allScope())
            val projectFileIndex = ProjectFileIndex.getInstance(project)
            val files = mutableListOf<VirtualFile>()
            for (templatesFolder in templatesFolders) {
                val children = templatesFolder?.takeIf { ScriptDefinitionMarkerFileType.isParentOfMyFileType(it) }
                    ?.takeIf { projectFileIndex.isInLibraryClasses(it) }?.children?.filter {
                        !it.name.endsWith(MAIN_KTS)
                    } ?: continue
                files += children
            }

            getTemplateClassPath(project, files)
        }
    }

    private fun getTemplateClassPath(project: Project, files: Collection<VirtualFile>): DefinitionTemplates {
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
                it is LibraryOrSdkOrderEntry && it.getRootFiles(OrderRootType.CLASSES).contains(root)
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
                    classpath.addIfNotNull(localVirtualFile.fileSystem.getNioPath(localVirtualFile))
                }
            }
        }

        return DefinitionTemplates(templates.toList(), classpath.map { it.absolutePathString() })
    }
}
