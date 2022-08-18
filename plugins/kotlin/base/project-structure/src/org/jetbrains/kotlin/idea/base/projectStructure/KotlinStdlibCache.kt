// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.messages.MessageBusConnection
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
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
        return stdlibCache[libraryInfo]
    }

    override fun isStdlibDependency(libraryInfo: LibraryInfo): Boolean = stdlibDependencyCache[libraryInfo]

    override fun findStdlibInModuleDependencies(module: IdeaModuleInfo): LibraryInfo? {
        ProgressManager.checkCanceled()
        val stdlibDependency = moduleStdlibDependencyCache.get(module)
        return stdlibDependency.libraryInfo
    }

    override fun dispose() = Unit

    private abstract class BaseStdLibCache(project: Project) : SynchronizedFineGrainedEntityCache<LibraryInfo, Boolean>(project, cleanOnLowMemory = true),
                                                               OutdatedLibraryInfoListener {
        override fun subscribe() {
            val busConnection = project.messageBus.connect(this)
            busConnection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
        }

        override fun checkKeyValidity(key: LibraryInfo) {
            key.checkValidity()
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            super.invalidateKeys(libraryInfos) { _, _ -> true }
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

    private inner class ModuleStdlibDependencyCache : Disposable {
        private val libraryCache = LibraryCache()
        private val sdkCache = SdkCache()
        private val moduleCache = ModuleCache()

        init {
            Disposer.register(this, libraryCache)
            Disposer.register(this, sdkCache)
            Disposer.register(this, moduleCache)
        }

        fun get(key: IdeaModuleInfo): StdlibDependency =
            when(key) {
                is LibraryInfo -> libraryCache[key]
                is SdkInfo -> sdkCache[key]
                else -> moduleCache[key]
            }

        override fun dispose() = Unit

        private abstract inner class AbstractCache<Key : IdeaModuleInfo> :
            SynchronizedFineGrainedEntityCache<Key, StdlibDependency>(project, cleanOnLowMemory = true),
            OutdatedLibraryInfoListener,
            ProjectJdkTable.Listener,
            ModuleRootListener {
            override fun subscribe() {
                val connection = project.messageBus.connect(this)
                connection.subscribe(OutdatedLibraryInfoListener.TOPIC, this)
                connection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, this)
                connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
                subscribe(connection)
            }

            protected open fun subscribe(connection: MessageBusConnection) {
            }

            protected fun Key.findStdLib(): LibraryInfo? = dependencies().firstOrNull {
                it is LibraryInfo && isStdlib(it)
            } as LibraryInfo?

            protected fun LibraryInfo?.toStdlibDependency(): StdlibDependency {
                if (this == null && runReadAction { project.isDisposed || DumbService.isDumb(project) }) {
                    throw ProcessCanceledException()
                }

                return StdlibDependency(this)
            }

            override fun checkValueValidity(value: StdlibDependency) {
                value.libraryInfo?.checkValidity()
            }

            override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
                invalidateEntries({ _, v -> v.libraryInfo in libraryInfos }, validityCondition = { _, v -> v.libraryInfo != null })
            }

            override fun jdkRemoved(jdk: Sdk) {
                invalidateEntries({ k, _ -> k.safeAs<SdkInfo>()?.sdk == jdk })
            }

            override fun jdkNameChanged(jdk: Sdk, previousName: String) {
                jdkRemoved(jdk)
            }

            override fun rootsChanged(event: ModuleRootEvent) {
                // SDK could be changed (esp in tests) out of message bus subscription
                val sdks = project.allSdks()
                invalidateEntries(
                    { k, _ -> k.safeAs<SdkInfo>()?.let { it.sdk !in sdks } == true  },
                    // unable to check entities properly: an event could be not the last
                    validityCondition = null
                )
            }
        }

        private inner class LibraryCache : AbstractCache<LibraryInfo>() {
            override fun calculate(key: LibraryInfo): StdlibDependency {
                val stdLib = key.takeIf(::isStdlib) ?: key.findStdLib()

                return stdLib.toStdlibDependency()
            }

            fun putExtraValues(map: Map<LibraryInfo, StdlibDependency>) {
                putAll(map)
            }

            override fun checkKeyValidity(key: LibraryInfo) {
                key.checkValidity()
            }

            override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
                invalidateEntries({ k, v -> k in libraryInfos || v.libraryInfo in libraryInfos })
            }
        }

        private inner class SdkCache : AbstractCache<SdkInfo>() {
            override fun calculate(key: SdkInfo): StdlibDependency =
                key.findStdLib().toStdlibDependency()

            override fun checkKeyValidity(key: SdkInfo) = Unit

        }

        private inner class ModuleCache : AbstractCache<IdeaModuleInfo>(), WorkspaceModelChangeListener {
            override fun subscribe(connection: MessageBusConnection) {
                WorkspaceModelTopics.getInstance(project).subscribeImmediately(connection, this)
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
                            LibraryInfoCache.getInstance(project)[it]
                        }?.firstOrNull(::isStdlib)?.let {
                            return@index it
                        }
                    }
                    null
                } ?: key.findStdLib()

                val stdlibDependency = stdLib.toStdlibDependency()

                moduleSourceInfo?.let {
                    val result = hashMapOf<LibraryInfo, StdlibDependency>()
                    // all module dependencies have same stdlib as module itself
                    key.dependencies().forEach {
                        if (it is LibraryInfo) {
                            result[it] = stdlibDependency
                        }
                    }
                    libraryCache.putExtraValues(result)
                }

                return stdlibDependency
            }

            override fun checkKeyValidity(key: IdeaModuleInfo) {
                key.checkValidity()
            }

            override fun changed(event: VersionedStorageChange) {
                event.getChanges(ModuleEntity::class.java).ifEmpty { return }
                invalidateEntries({ k, _ -> k !is LibraryInfo && k !is SdkInfo }, validityCondition = null)
            }
        }
    }
}

fun LibraryInfo.isCoreKotlinLibrary(project: Project): Boolean =
    isKotlinStdlib(project) || isKotlinStdlibDependency(project)

fun LibraryInfo.isKotlinStdlib(project: Project): Boolean =
    KotlinStdlibCache.getInstance(project).isStdlib(this)

fun LibraryInfo.isKotlinStdlibDependency(project: Project): Boolean =
    KotlinStdlibCache.getInstance(project).isStdlibDependency(this)