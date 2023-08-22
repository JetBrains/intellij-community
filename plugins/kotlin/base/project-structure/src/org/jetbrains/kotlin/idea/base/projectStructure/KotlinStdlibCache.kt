// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DumbModeAccessType
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.messages.MessageBusConnection
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.base.util.caching.SynchronizedFineGrainedEntityCache
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
        private val noStdlibDependency = StdlibDependency(null)
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

        override fun contains(file: VirtualFile): Boolean = file.fileSystem in fileSystems && VfsUtilCore.isUnder(file, directories)

        override fun toString() = "All files under: $directories"
    }

    private fun libraryScopeContainsIndexedFilesForName(libraryInfo: LibraryInfo, name: FqName): Boolean {
        val libraryScope = LibraryScope(project, libraryInfo.library.getFiles(OrderRootType.CLASSES).toSet())
        return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
            FileBasedIndex.getInstance().getContainingFilesIterator(KotlinStdlibIndex.NAME, name, libraryScope).hasNext()
        })
    }

    private fun isFatJar(libraryInfo: LibraryInfo) = libraryInfo.getLibraryRoots().size > 1

    private fun isKotlinJavaRuntime(libraryInfo: LibraryInfo) = libraryInfo.library.name == KOTLIN_JAVA_RUNTIME_NAME

    override fun isStdlib(libraryInfo: LibraryInfo): Boolean = stdlibCache[libraryInfo]

    override fun isStdlibDependency(libraryInfo: LibraryInfo): Boolean = stdlibDependencyCache[libraryInfo]

    override fun findStdlibInModuleDependencies(module: IdeaModuleInfo): LibraryInfo? {
        ProgressManager.checkCanceled()
        val stdlibDependency = moduleStdlibDependencyCache.get(module)
        return stdlibDependency.libraryInfo
    }

    override fun dispose() = Unit

    private sealed class BaseStdLibCache(project: Project) :
        SynchronizedFineGrainedEntityCache<LibraryInfo, Boolean>(project, doSelfInitialization = false, cleanOnLowMemory = true),
        LibraryInfoListener {
        override fun subscribe() {
            val busConnection = project.messageBus.connect(this)
            busConnection.subscribe(LibraryInfoListener.TOPIC, this)
        }

        override fun checkKeyValidity(key: LibraryInfo) {
            key.checkValidity()
        }

        override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
            invalidateKeys(libraryInfos)
        }
    }

    private fun LibraryInfo.isStdlibWithFile(fileName: FqName): Boolean {
        if (isFatJar(this) && !isKotlinJavaRuntime(this)) return false
        return libraryScopeContainsIndexedFilesForName(this, fileName)
    }

    private inner class StdLibCache : BaseStdLibCache(project) {
        override fun calculate(key: LibraryInfo): Boolean = key.isStdlibWithFile(KotlinStdlibIndex.KOTLIN_STDLIB_NAME)
    }

    private inner class StdlibDependencyCache : BaseStdLibCache(project) {
        override fun calculate(key: LibraryInfo): Boolean = key.isStdlibWithFile(KotlinStdlibIndex.STANDARD_LIBRARY_DEPENDENCY_NAME)
    }

    private inner class ModuleStdlibDependencyCache : Disposable {
        private val libraryCache = LibraryCache()
        private val moduleCache = ModuleCache()

        init {
            Disposer.register(this, libraryCache)
            Disposer.register(this, moduleCache)
        }

        fun get(key: IdeaModuleInfo): StdlibDependency = when (key) {
            is LibraryInfo -> libraryCache[key]
            is SdkInfo, is NotUnderContentRootModuleInfo -> noStdlibDependency
            else -> moduleCache[key]
        }

        override fun dispose() = Unit

        private abstract inner class AbstractCache<Key : IdeaModuleInfo> :
            SynchronizedFineGrainedEntityCache<Key, StdlibDependency>(project, doSelfInitialization = false, cleanOnLowMemory = true),
            LibraryInfoListener {
            override fun subscribe() {
                val connection = project.messageBus.connect(this)
                connection.subscribe(LibraryInfoListener.TOPIC, this)
                subscribe(connection)
            }

            protected open fun subscribe(connection: MessageBusConnection) {
            }

            protected fun Key.findStdLib(): LibraryInfo? {
                val dependencies = if (this is LibraryInfo) {
                    if (isStdlib(this)) return this

                    LibraryDependenciesCache.getInstance(project).getLibraryDependencies(this).libraries
                } else {
                    dependencies()
                }

                return dependencies.firstNotNullOfOrNull { it.safeAs<LibraryInfo>()?.takeIf(::isStdlib) }
            }

            protected fun LibraryInfo?.toStdlibDependency(): StdlibDependency {
                if (this != null) {
                    return StdlibDependency(this)
                }

                val flag = runReadAction {
                    when {
                        project.isDisposed -> null
                        DumbService.isDumb(project) -> true
                        else -> false
                    }
                }

                return when (flag) {
                    null -> throw ProcessCanceledException()
                    true -> throw IndexNotReadyException.create()
                    else -> noStdlibDependency
                }
            }

            override fun checkValueValidity(value: StdlibDependency) {
                value.libraryInfo?.checkValidity()
            }
        }

        private inner class LibraryCache : AbstractCache<LibraryInfo>() {
            override fun calculate(key: LibraryInfo): StdlibDependency = key.findStdLib().toStdlibDependency()

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

        private inner class ModuleCache : AbstractCache<IdeaModuleInfo>(), WorkspaceModelChangeListener {
            override fun subscribe(connection: MessageBusConnection) {
                connection.subscribe(WorkspaceModelTopics.CHANGED, this)
            }

            override fun calculate(key: IdeaModuleInfo): StdlibDependency {
                val moduleSourceInfo = key.safeAs<ModuleSourceInfo>()
                val stdLib = moduleSourceInfo?.module?.moduleWithLibrariesScope?.let index@{ scope ->
                    val stdlibManifests = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                        FileBasedIndex.getInstance().getContainingFiles(
                          KotlinStdlibIndex.NAME,
                          KotlinStdlibIndex.KOTLIN_STDLIB_NAME,
                          scope
                        )
                    })

                    val projectFileIndex = ProjectFileIndex.getInstance(project)
                    val libraryInfoCache = LibraryInfoCache.getInstance(project)
                    for (manifest in stdlibManifests) {
                        val orderEntries = projectFileIndex.getOrderEntriesForFile(manifest)
                        for (entry in orderEntries) {
                            val library = entry.safeAs<LibraryOrderEntry>()?.library.safeAs<LibraryEx>() ?: continue
                            val libraryInfos = libraryInfoCache[library]
                            return@index libraryInfos.find(::isStdlib) ?: continue
                        }
                    }
                    null
                } ?: key.findStdLib()

                return stdLib.toStdlibDependency()
            }

            override fun postProcessNewValue(key: IdeaModuleInfo, value: StdlibDependency) {
                if (key !is ModuleSourceInfo) return

                val result = hashMapOf<LibraryInfo, StdlibDependency>()
                // all module dependencies have same stdlib as module itself
                key.dependencies().forEach {
                    if (it is LibraryInfo) {
                        result[it] = value
                    }
                }

                libraryCache.putExtraValues(result)
            }

            override fun checkKeyValidity(key: IdeaModuleInfo) {
                key.checkValidity()
            }

            override fun libraryInfosRemoved(libraryInfos: Collection<LibraryInfo>) {
                invalidateEntries({ _, v -> v.libraryInfo in libraryInfos }, validityCondition = { _, v -> v.libraryInfo != null })
            }

            override fun changed(event: VersionedStorageChange) {
                event.getChanges(ModuleEntity::class.java).ifEmpty { return }

                invalidate(writeAccessRequired = true)
            }
        }
    }
}

fun LibraryInfo.isCoreKotlinLibrary(project: Project): Boolean = isKotlinStdlib(project) || isKotlinStdlibDependency(project)

fun LibraryInfo.isKotlinStdlib(project: Project): Boolean = KotlinStdlibCache.getInstance(project).isStdlib(this)

fun LibraryInfo.isKotlinStdlibDependency(project: Project): Boolean = KotlinStdlibCache.getInstance(project).isStdlibDependency(this)