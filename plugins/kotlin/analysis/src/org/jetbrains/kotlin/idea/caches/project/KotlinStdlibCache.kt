/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.idea.configuration.IdeBuiltInsLoadingState
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.ConcurrentHashMap

// TODO(kirpichenkov): works only for JVM (see KT-44552)
interface KotlinStdlibCache {
    fun isStdlib(libraryInfo: LibraryInfo): Boolean
    fun isStdlibDependency(libraryInfo: LibraryInfo): Boolean
    fun findStdlibInModuleDependencies(module: IdeaModuleInfo): LibraryInfo?

    companion object {
        fun getInstance(project: Project): KotlinStdlibCache =
            if (IdeBuiltInsLoadingState.isFromClassLoader) {
                Disabled
            } else {
                project.getService(KotlinStdlibCache::class.java)
                    ?: error("Failed to load service ${KotlinStdlibCache::class.java.name}")
            }

        val Disabled = object : KotlinStdlibCache {
            override fun isStdlib(libraryInfo: LibraryInfo) = false
            override fun isStdlibDependency(libraryInfo: LibraryInfo) = false
            override fun findStdlibInModuleDependencies(module: IdeaModuleInfo): LibraryInfo? = null
        }
    }
}

class KotlinStdlibCacheImpl(val project: Project) : KotlinStdlibCache {
    companion object {
        private const val KOTLIN_JAVA_RUNTIME_NAME = "KotlinJavaRuntime"
    }

    @JvmInline
    private value class StdlibDependency(val libraryInfo: LibraryInfo?)

    private val isStdlibCache: MutableMap<LibraryInfo, Boolean>
        get() = project.cacheInvalidatingOnRootModifications {
            ConcurrentHashMap<LibraryInfo, Boolean>()
        }

    private val isStdlibDependencyCache: MutableMap<LibraryInfo, Boolean>
        get() = project.cacheInvalidatingOnRootModifications {
            ConcurrentHashMap<LibraryInfo, Boolean>()
        }

    private val moduleStdlibDependencyCache: MutableMap<IdeaModuleInfo, StdlibDependency>
        get() = project.cacheInvalidatingOnRootModifications {
            ConcurrentHashMap<IdeaModuleInfo, StdlibDependency>()
        }

    private class LibraryScope(
        project: Project,
        private val directories: Set<VirtualFile>
    ) : DelegatingGlobalSearchScope(GlobalSearchScope.allScope(project)) {
        private val fileSystems = directories.mapTo(hashSetOf(), VirtualFile::getFileSystem)

        override fun contains(file: VirtualFile): Boolean =
            file.fileSystem in fileSystems && generateSequence(file, VirtualFile::getParent).any { it in directories }

        override fun toString() = "All files under: $directories"
    }

    private fun libraryScopeContainsIndexedFilesForNames(libraryInfo: LibraryInfo, names: Collection<FqName>): Boolean =
        names.any { name ->
            DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                FileBasedIndex.getInstance().getContainingFiles(
                    KotlinStdlibIndex.KEY,
                    name,
                    LibraryScope(project, libraryInfo.library.rootProvider.getFiles(OrderRootType.CLASSES).toSet())
                ).isNotEmpty()
            })
        }

    private fun libraryScopeContainsIndexedFilesForName(libraryInfo: LibraryInfo, name: FqName) =
        libraryScopeContainsIndexedFilesForNames(libraryInfo, listOf(name))

    private fun isFatJar(libraryInfo: LibraryInfo) =
        libraryInfo.getLibraryRoots().size > 1

    private fun isKotlinJavaRuntime(libraryInfo: LibraryInfo) =
        libraryInfo.library.name == KOTLIN_JAVA_RUNTIME_NAME

    override fun isStdlib(libraryInfo: LibraryInfo): Boolean {
        return isStdlibCache.getOrPut(libraryInfo) {
            libraryScopeContainsIndexedFilesForName(libraryInfo, KotlinStdlibIndex.KOTLIN_STDLIB_NAME) &&
                    (!isFatJar(libraryInfo) || isKotlinJavaRuntime(libraryInfo))
        }
    }

    override fun isStdlibDependency(libraryInfo: LibraryInfo): Boolean {
        return isStdlibDependencyCache.getOrPut(libraryInfo) {
            libraryScopeContainsIndexedFilesForNames(libraryInfo, KotlinStdlibIndex.STANDARD_LIBRARY_DEPENDENCY_NAMES) &&
                    (!isFatJar(libraryInfo) || isKotlinJavaRuntime(libraryInfo))
        }
    }

    override fun findStdlibInModuleDependencies(module: IdeaModuleInfo): LibraryInfo? {
        ProgressManager.checkCanceled()
        val stdlibDependency = moduleStdlibDependencyCache.getOrPut(module) {
            val moduleSourceInfo = module.safeAs<ModuleSourceInfo>()
            val stdLib = moduleSourceInfo?.module?.moduleWithLibrariesScope?.let index@{ scope ->
                val stdlibManifests = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                    FileBasedIndex.getInstance().getContainingFiles(
                        KotlinStdlibIndex.KEY,
                        KotlinStdlibIndex.KOTLIN_STDLIB_NAME,
                        scope
                    )
                })
                val index = ProjectFileIndex.SERVICE.getInstance(project)
                for (manifest in stdlibManifests) {
                    val orderEntries = index.getOrderEntriesForFile(manifest)
                    orderEntries.firstNotNullOfOrNull { it.safeAs<LibraryOrderEntry>()?.library.safeAs<LibraryEx>() }?.let {
                        createLibraryInfo(project, it)
                    }?.firstOrNull(::isStdlib)?.let {
                        return@index it
                    }
                }
                null
            } ?: module.safeAs<LibraryInfo>()?.takeIf(::isStdlib) ?: module.dependencies().firstOrNull {
                it is LibraryInfo && isStdlib(it)
            } as LibraryInfo?

            if (stdLib == null && runReadAction { project.isDisposed || DumbService.isDumb(project) }) {
                throw ProcessCanceledException()
            }

            val stdlibDependency = StdlibDependency(stdLib)
            moduleSourceInfo?.let { _ ->
                // all module dependencies have same stdlib as module itself
                module.dependencies().forEach {
                    if (it is LibraryInfo) {
                        moduleStdlibDependencyCache.putIfAbsent(it, stdlibDependency)
                    }
                }
            }

            stdlibDependency
        }

        return stdlibDependency.libraryInfo
    }
}

fun LibraryInfo.isCoreKotlinLibrary(project: Project): Boolean =
    isKotlinStdlib(project) || isKotlinStdlibDependency(project)

fun LibraryInfo.isKotlinStdlib(project: Project): Boolean =
    KotlinStdlibCache.getInstance(project).isStdlib(this)

fun LibraryInfo.isKotlinStdlibDependency(project: Project): Boolean =
    KotlinStdlibCache.getInstance(project).isStdlibDependency(this)
