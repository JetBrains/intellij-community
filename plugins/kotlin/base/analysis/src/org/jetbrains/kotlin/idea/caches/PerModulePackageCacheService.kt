// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.PsiTreeChangePreprocessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.firstOrNull
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfoOrNull
import org.jetbrains.kotlin.idea.caches.PerModulePackageCacheService.Companion.DEBUG_LOG_ENABLE_PerModulePackageCache
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.ModuleSourceInfo
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getSourceRoot
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.idea.util.sourceRoot
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPackageDirective
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class KotlinPackageStatementPsiTreeChangePreprocessor(private val project: Project) : PsiTreeChangePreprocessor {
    override fun treeChanged(event: PsiTreeChangeEventImpl) {
        // skip events out of scope of this processor
        when (event.code) {
            PsiTreeChangeEventImpl.PsiEventType.CHILD_ADDED,
            PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED,
            PsiTreeChangeEventImpl.PsiEventType.CHILD_REPLACED,
            PsiTreeChangeEventImpl.PsiEventType.CHILD_REMOVED,
            PsiTreeChangeEventImpl.PsiEventType.CHILDREN_CHANGED -> Unit
            else -> return
        }

        val eFile = event.file ?: event.child ?: run {
            LOG.debugIfEnabled(project, true) { "Got PsiEvent: $event without file/child" }
            return
        }

        val file = eFile as? KtFile ?: return

        when (event.code) {
            PsiTreeChangeEventImpl.PsiEventType.CHILD_ADDED,
            PsiTreeChangeEventImpl.PsiEventType.CHILD_MOVED,
            PsiTreeChangeEventImpl.PsiEventType.CHILD_REPLACED,
            PsiTreeChangeEventImpl.PsiEventType.CHILD_REMOVED -> {
                val child = event.child ?: run {
                    LOG.debugIfEnabled(project, true) { "Got PsiEvent: $event without child" }
                    return
                }
                if (child.getParentOfType<KtPackageDirective>(false) != null)
                    PerModulePackageCacheService.getInstance(project).notifyPackageChange(file)
            }
            PsiTreeChangeEventImpl.PsiEventType.CHILDREN_CHANGED -> {
                val parent = event.parent ?: run {
                    LOG.debugIfEnabled(project, true) { "Got PsiEvent: $event without parent" }
                    return
                }
                val childrenOfType = parent.getChildrenOfType<KtPackageDirective>()
                if (
                    (!event.isGenericChange && (childrenOfType.any() || parent is KtPackageDirective)) ||
                    (childrenOfType.any { it.name.isEmpty() } && parent is KtFile)
                ) {
                    PerModulePackageCacheService.getInstance(project).notifyPackageChange(file)
                }
            }
            else -> error("unsupported event code ${event.code} for PsiEvent $event")
        }
    }

    companion object {
        val LOG = Logger.getInstance(this::class.java)
    }
}

private typealias ImplicitPackageData = MutableMap<FqName, MutableList<VirtualFile>>

class ImplicitPackagePrefixCache(private val project: Project) {
    private val implicitPackageCache = ConcurrentHashMap<VirtualFile, ImplicitPackageData>()

    fun getPrefix(sourceRoot: VirtualFile): FqName {
        val implicitPackageMap = implicitPackageCache.getOrPut(sourceRoot) { analyzeImplicitPackagePrefixes(sourceRoot) }
        return implicitPackageMap.keys.singleOrNull() ?: FqName.ROOT
    }

    internal fun clear() {
        implicitPackageCache.clear()
    }

    private fun analyzeImplicitPackagePrefixes(sourceRoot: VirtualFile): MutableMap<FqName, MutableList<VirtualFile>> {
        val result = mutableMapOf<FqName, MutableList<VirtualFile>>()
        val ktFiles = sourceRoot.children.filter(VirtualFile::isKotlinFileType)
        for (ktFile in ktFiles) {
            result.addFile(ktFile)
        }
        return result
    }

    private fun ImplicitPackageData.addFile(ktFile: VirtualFile) {
        synchronized(this) {
            val psiFile = PsiManager.getInstance(project).findFile(ktFile) as? KtFile ?: return
            addPsiFile(psiFile, ktFile)
        }
    }

    private fun ImplicitPackageData.addPsiFile(
        psiFile: KtFile,
        ktFile: VirtualFile
    ) = getOrPut(psiFile.packageFqName) { mutableListOf() }.add(ktFile)

    private fun ImplicitPackageData.removeFile(file: VirtualFile) {
        synchronized(this) {
            for ((key, value) in this) {
                if (value.remove(file)) {
                    if (value.isEmpty()) remove(key)
                    break
                }
            }
        }
    }

    private fun ImplicitPackageData.updateFile(file: KtFile) {
        synchronized(this) {
            removeFile(file.virtualFile)
            addPsiFile(file, file.virtualFile)
        }
    }

    internal fun update(event: VFileEvent) {
        when (event) {
            is VFileCreateEvent -> checkNewFileInSourceRoot(event.file)
            is VFileDeleteEvent -> checkDeletedFileInSourceRoot(event.file)
            is VFileCopyEvent -> {
                val newParent = event.newParent
                if (newParent.isValid) {
                    checkNewFileInSourceRoot(newParent.findChild(event.newChildName))
                }
            }
            is VFileMoveEvent -> {
                checkNewFileInSourceRoot(event.file)
                if (event.oldParent.getSourceRoot(project) == event.oldParent) {
                    implicitPackageCache[event.oldParent]?.removeFile(event.file)
                }
            }
        }
    }

    private fun checkNewFileInSourceRoot(file: VirtualFile?) {
        if (file == null) return
        if (file.getSourceRoot(project) == file.parent) {
            implicitPackageCache[file.parent]?.addFile(file)
        }
    }

    private fun checkDeletedFileInSourceRoot(file: VirtualFile?) {
        val directory = file?.parent
        if (directory == null || !directory.isValid) return
        if (directory.getSourceRoot(project) == directory) {
            implicitPackageCache[directory]?.removeFile(file)
        }
    }

    internal fun update(ktFile: KtFile) {
        val parent = ktFile.virtualFile?.parent ?: return
        if (ktFile.sourceRoot == parent) {
            implicitPackageCache[parent]?.updateFile(ktFile)
        }
    }
}

class PerModulePackageCacheService(private val project: Project) : Disposable {

    /*
     * Actually an WeakMap<Module, ConcurrentMap<ModuleSourceInfo, ConcurrentMap<FqName, Boolean>>>
     */
    private val cache = ContainerUtil.createConcurrentWeakMap<Module, ConcurrentMap<ModuleSourceInfo, ConcurrentMap<FqName, Boolean>>>()
    private val implicitPackagePrefixCache = ImplicitPackagePrefixCache(project)

    private val useStrongMapForCaching = Registry.`is`("kotlin.cache.packages.strong.map", false)

    private val pendingVFileChanges: MutableSet<VFileEvent> = mutableSetOf()
    private val pendingKtFileChanges: MutableSet<KtFile> = mutableSetOf()

    private val projectScope = GlobalSearchScope.projectScope(project)

    internal fun onTooComplexChange() {
        clear()
    }

    private fun clear() {
        synchronized(this) {
            pendingVFileChanges.clear()
            pendingKtFileChanges.clear()
            cache.clear()
            implicitPackagePrefixCache.clear()
        }
    }

    internal fun notifyPackageChange(file: VFileEvent): Unit = synchronized(this) {
        pendingVFileChanges += file
    }

    internal fun notifyPackageChange(file: KtFile): Unit = synchronized(this) {
        pendingKtFileChanges += file
    }

    private fun invalidateCacheForModuleSourceInfo(moduleSourceInfo: ModuleSourceInfo) {
        LOG.debugIfEnabled(project) { "Invalidated cache for $moduleSourceInfo" }
        val perSourceInfoData = cache[moduleSourceInfo.module] ?: return
        val dataForSourceInfo = perSourceInfoData[moduleSourceInfo] ?: return
        dataForSourceInfo.clear()
    }

    private fun checkPendingChanges() = synchronized(this) {
        if (pendingVFileChanges.size + pendingKtFileChanges.size >= FULL_DROP_THRESHOLD) {
            onTooComplexChange()
        } else {
            pendingVFileChanges.processPending { event ->
                val vfile = event.file ?: return@processPending
                // When VirtualFile !isValid (deleted for example), it impossible to use getModuleInfoByVirtualFile
                // For directory we must check both is it in some sourceRoot, and is it contains some sourceRoot
                if (vfile.isDirectory || !vfile.isValid) {
                    for ((module, data) in cache) {
                        val sourceRootUrls = module.rootManager.sourceRootUrls
                        if (sourceRootUrls.any { url ->
                                vfile.containedInOrContains(url)
                            }) {
                            LOG.debugIfEnabled(project) { "Invalidated cache for $module" }
                            data.clear()
                        }
                    }
                } else {
                    val infoByVirtualFile = ModuleInfoProvider.getInstance(project).firstOrNull(vfile)
                    if (infoByVirtualFile == null || infoByVirtualFile !is ModuleSourceInfo) {
                        LOG.debugIfEnabled(project) { "Skip $vfile as it has mismatched ModuleInfo=$infoByVirtualFile" }
                    }
                    (infoByVirtualFile as? ModuleSourceInfo)?.let {
                        invalidateCacheForModuleSourceInfo(it)
                    }
                }

                implicitPackagePrefixCache.update(event)
            }

            pendingKtFileChanges.processPending { file ->
                if (file.virtualFile != null && file.virtualFile !in projectScope) {
                    LOG.debugIfEnabled(project) {
                        "Skip $file without vFile, or not in scope: ${file.virtualFile?.let { it !in projectScope }}"
                    }
                    return@processPending
                }
                val nullableModuleInfo = file.moduleInfoOrNull
                (nullableModuleInfo as? ModuleSourceInfo)?.let { invalidateCacheForModuleSourceInfo(it) }
                if (nullableModuleInfo == null || nullableModuleInfo !is ModuleSourceInfo) {
                    LOG.debugIfEnabled(project) { "Skip $file as it has mismatched ModuleInfo=$nullableModuleInfo" }
                }
                implicitPackagePrefixCache.update(file)
            }
        }
    }

    private inline fun <T> MutableCollection<T>.processPending(crossinline body: (T) -> Unit) {
        this.removeIf { value ->
            try {
                body(value)
            } catch (pce: ProcessCanceledException) {
                throw pce
            } catch (exc: Exception) {
                // Log and proceed. Otherwise pending object processing won't be cleared and exception will be thrown forever.
                LOG.error(exc)
            }

            return@removeIf true
        }
    }

    private fun VirtualFile.containedInOrContains(root: String) =
        (VfsUtilCore.isEqualOrAncestor(url, root)
                || isDirectory && VfsUtilCore.isEqualOrAncestor(root, url))


    fun packageExists(packageFqName: FqName, moduleInfo: ModuleSourceInfo): Boolean {
        val module = moduleInfo.module
        checkPendingChanges()

        val perSourceInfoCache = cache.getOrPut(module) {
            if (useStrongMapForCaching) ConcurrentHashMap() else CollectionFactory.createConcurrentSoftMap()
        }
        val cacheForCurrentModuleInfo = perSourceInfoCache.getOrPut(moduleInfo) {
            if (useStrongMapForCaching) ConcurrentHashMap() else CollectionFactory.createConcurrentSoftMap()
        }

        return try {
          cacheForCurrentModuleInfo.getOrPut(packageFqName) {
              val packageExists = KotlinPackageIndexUtils.packageExists(packageFqName, moduleInfo.contentScope)
              LOG.debugIfEnabled(project) { "Computed cache value for $packageFqName in $moduleInfo is $packageExists" }
              packageExists
          }
        } catch (e: IndexNotReadyException) {
            DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                KotlinPackageIndexUtils.packageExists(packageFqName, moduleInfo.contentScope)
            })
        }
    }

    fun getImplicitPackagePrefix(sourceRoot: VirtualFile): FqName {
        checkPendingChanges()
        return implicitPackagePrefixCache.getPrefix(sourceRoot)
    }

    override fun dispose() {
        clear()
    }

    companion object {
        const val FULL_DROP_THRESHOLD = 1000
        private val LOG = Logger.getInstance(this::class.java)

        fun getInstance(project: Project): PerModulePackageCacheService = project.service()

        var Project.DEBUG_LOG_ENABLE_PerModulePackageCache: Boolean
                by NotNullableUserDataProperty<Project, Boolean>(Key.create("debug.PerModulePackageCache"), false)
    }

    class PackageCacheBulkFileListener(private val project: Project) : BulkFileListener {
        override fun before(events: List<VFileEvent>) = onEvents(events, false)
        override fun after(events: List<VFileEvent>) = onEvents(events, true)

        private fun isRelevant(event: VFileEvent): Boolean = when (event) {
            is VFilePropertyChangeEvent -> false
            is VFileCreateEvent -> true
            is VFileMoveEvent -> true
            is VFileDeleteEvent -> true
            is VFileContentChangeEvent -> true
            is VFileCopyEvent -> true
            else -> {
                LOG.warn("Unknown vfs event: ${event.javaClass}")
                false
            }
        }

        private fun onEvents(events: List<VFileEvent>, isAfter: Boolean) {
            val service = getInstance(project)
            val fileManager = PsiManagerEx.getInstanceEx(project).fileManager
            if (events.size >= FULL_DROP_THRESHOLD) {
                service.onTooComplexChange()
            } else {
                events.asSequence()
                    .filter(::isRelevant)
                    .filter {
                        (it.isValid || it !is VFileCreateEvent) && it.file != null
                    }
                    .filter {
                        val vFile = it.file!!
                        vFile.isDirectory || vFile.isKotlinFileType()
                    }
                    .filter {
                        // It expected that content change events will be duplicated with more precise PSI events and processed
                        // in KotlinPackageStatementPsiTreeChangePreprocessor, but events might have been missing if PSI view provider
                        // is absent.
                        if (it is VFileContentChangeEvent) {
                            isAfter && fileManager.findCachedViewProvider(it.file) == null
                        } else {
                            true
                        }
                    }
                    .filter {
                        when (val origin = it.requestor) {
                            is Project -> origin == project
                            is PsiManager -> origin.project == project
                            else -> true
                        }
                    }
                    .forEach { event -> service.notifyPackageChange(event) }
            }
        }
    }

    class PackageCacheModuleRootListener(private val project: Project) : ModuleRootListener {
        override fun rootsChanged(event: ModuleRootEvent) {
            getInstance(project).onTooComplexChange()
        }
    }
}

private fun Logger.debugIfEnabled(project: Project, withCurrentTrace: Boolean = false, message: () -> String) {
    if (isUnitTestMode() && project.DEBUG_LOG_ENABLE_PerModulePackageCache) {
        val msg = message()
        if (withCurrentTrace) {
            val e = Exception().apply { fillInStackTrace() }
            this.debug(msg, e)
        } else {
            this.debug(msg)
        }
    }
}