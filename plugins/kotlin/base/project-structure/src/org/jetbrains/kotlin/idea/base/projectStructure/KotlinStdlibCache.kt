// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.util.caching.FineGrainedEntityCache
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.idea.vfilefinder.KotlinStdlibIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

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

internal class KotlinStdlibCacheImpl(private val project: Project) : KotlinStdlibCache, Disposable {
    companion object {
        private const val KOTLIN_JAVA_RUNTIME_NAME = "KotlinJavaRuntime"
    }

    @JvmInline
    private value class StdlibDependency(val libraryInfo: LibraryInfo?)

    private val stdlibCache = StdLibCache()
    private val stdlibDependencyCache = StdlibDependencyCache()
    private val moduleStdlibDependencyCache = ModuleStdlibDependencyCache()

    init {
      Disposer.register(this, stdlibCache)
      Disposer.register(this, stdlibDependencyCache)
      Disposer.register(this, moduleStdlibDependencyCache)
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
        return stdlibCache.get(libraryInfo)
    }

    override fun isStdlibDependency(libraryInfo: LibraryInfo): Boolean = stdlibDependencyCache.get(libraryInfo)

    override fun findStdlibInModuleDependencies(module: IdeaModuleInfo): LibraryInfo? {
        ProgressManager.checkCanceled()
        val stdlibDependency = moduleStdlibDependencyCache.get(module)
        return stdlibDependency.libraryInfo
    }

    override fun dispose() = Unit

    private abstract class BaseStdLibCache(project: Project) : FineGrainedEntityCache<LibraryInfo, Boolean>(project, cleanOnLowMemory = true),
                                                               OutdatedLibraryInfoListener {
        override fun subscribe() {
            val busConnection = project.messageBus.connect(this)
            busConnection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
        }

        override fun checkValidity(key: LibraryInfo) {
            val library = key.library
            library.checkValidity()
        }

        override fun globalDependencies(key: LibraryInfo, value: Boolean): List<Any> =
            listOf(ProjectRootModificationTracker.getInstance(project))

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            super.invalidateKeys(libraryInfos) { true }
        }

    }

    private inner class StdLibCache : BaseStdLibCache(project) {
        override fun calculate(key: LibraryInfo): Boolean =
            libraryScopeContainsIndexedFilesForName(key, KotlinStdlibIndex.KOTLIN_STDLIB_NAME) &&
                    (!isFatJar(key) || isKotlinJavaRuntime(key))
    }

    private inner class StdlibDependencyCache : BaseStdLibCache(project) {
        override fun calculate(key: LibraryInfo): Boolean =
            libraryScopeContainsIndexedFilesForNames(key, KotlinStdlibIndex.STANDARD_LIBRARY_DEPENDENCY_NAMES) &&
                    (!isFatJar(key) || isKotlinJavaRuntime(key))

    }

    private inner class ModuleStdlibDependencyCache :
        FineGrainedEntityCache<IdeaModuleInfo, StdlibDependency>(project, cleanOnLowMemory = true),
        WorkspaceModelChangeListener,
        OutdatedLibraryInfoListener,
        ProjectJdkTable.Listener {

        override fun changed(event: VersionedStorageChange) {
            event.getChanges(ModuleEntity::class.java).ifEmpty { return }

            // libs and sdks are invalidated by its own listeners
            val condition: (IdeaModuleInfo) -> Boolean = { it !is LibraryInfo && it !is SdkInfo }
            invalidateKeys(condition, condition)
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            invalidateKeys(libraryInfos) { it is LibraryInfo }
        }

        override fun jdkRemoved(jdk: Sdk) {
            invalidateKeys({ it is SdkInfo && it.sdk == jdk }, { it is SdkInfo })
        }

        override fun jdkNameChanged(jdk: Sdk, previousName: String) {
            jdkRemoved(jdk)
        }

        override fun subscribe() {
            val busConnection = project.messageBus.connect(this)
            WorkspaceModelTopics.getInstance(project).subscribeImmediately(busConnection, this)
            busConnection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
            busConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
        }

        override fun calculate(key: IdeaModuleInfo): StdlibDependency {
            val moduleSourceInfo = key.safeAs<ModuleSourceInfo>()
            val stdLib = moduleSourceInfo?.module?.moduleWithLibrariesScope?.let index@{ scope ->
                val stdlibManifests = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                    FileBasedIndex.getInstance().getContainingFiles(
                        KotlinStdlibIndex.KEY,
                        KotlinStdlibIndex.KOTLIN_STDLIB_NAME,
                        scope
                    )
                })
                val index = ProjectFileIndex.getInstance(project)
                for (manifest in stdlibManifests) {
                    val orderEntries = index.getOrderEntriesForFile(manifest)
                    orderEntries.firstNotNullOfOrNull { it.safeAs<LibraryOrderEntry>()?.library.safeAs<LibraryEx>() }?.let {
                        LibraryInfoCache.getInstance(project).get(it)
                    }?.firstOrNull(::isStdlib)?.let {
                        return@index it
                    }
                }
                null
            } ?: key.safeAs<LibraryInfo>()?.takeIf(::isStdlib) ?: key.dependencies().firstOrNull {
                it is LibraryInfo && isStdlib(it)
            } as LibraryInfo?

            if (stdLib == null && runReadAction { project.isDisposed || DumbService.isDumb(project) }) {
                throw ProcessCanceledException()
            }

            return StdlibDependency(stdLib)
        }

        override fun extraCalculatedValues(key: IdeaModuleInfo, value: StdlibDependency): Map<IdeaModuleInfo, StdlibDependency>? {
            if (key !is ModuleSourceInfo) {
                return null
            }

            val result = hashMapOf<IdeaModuleInfo, StdlibDependency>()
            // all module dependencies have same stdlib as module itself
            key.dependencies().forEach {
                if (it is LibraryInfo) {
                    result[it] = value
                }
            }
            return result
        }

        override fun checkValidity(key: IdeaModuleInfo) {
            when (key) {
                is LibraryInfo -> key.library.checkValidity()
                is ModuleProductionSourceInfo -> key.module.checkValidity()
            }
        }

        override fun globalDependencies(key: IdeaModuleInfo, value: StdlibDependency): List<Any> =
            listOf(ProjectRootModificationTracker.getInstance(project))

    }

}

fun LibraryInfo.isCoreKotlinLibrary(project: Project): Boolean =
    isKotlinStdlib(project) || isKotlinStdlibDependency(project)

fun LibraryInfo.isKotlinStdlib(project: Project): Boolean =
    KotlinStdlibCache.getInstance(project).isStdlib(this)

fun LibraryInfo.isKotlinStdlibDependency(project: Project): Boolean =
    KotlinStdlibCache.getInstance(project).isStdlibDependency(this)

private fun Library.checkValidity() {
    if (this is LibraryEx && isDisposed) {
        throw AlreadyDisposedException("Library ${name} is already disposed")
    }
}
private fun Module.checkValidity() {
    if (isDisposed) {
        throw AlreadyDisposedException("Module ${name} is already disposed")
    }
}
