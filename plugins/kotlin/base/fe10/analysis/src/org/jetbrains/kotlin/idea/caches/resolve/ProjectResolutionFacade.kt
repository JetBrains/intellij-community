// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.containers.SLRUCache
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.caches.resolve.PlatformAnalysisSettings
import org.jetbrains.kotlin.context.GlobalContextImpl
import org.jetbrains.kotlin.context.withProject
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleInfoProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NotUnderContentRootModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.checkValidity
import org.jetbrains.kotlin.idea.base.scripting.projectStructure.ScriptDependenciesInfo
import org.jetbrains.kotlin.idea.caches.project.LibraryModificationTracker
import org.jetbrains.kotlin.idea.caches.project.getModuleInfosFromIdeaModel
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.js.resolve.diagnostics.ErrorsJs
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.CompositeBindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.konan.diagnostics.ErrorsNative
import org.jetbrains.kotlin.storage.CancellableSimpleLock
import org.jetbrains.kotlin.storage.SimpleLock
import org.jetbrains.kotlin.storage.guarded
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

internal class ProjectResolutionFacade(
  private val debugString: String,
  private val resolverDebugName: String,
  val project: Project,
  val globalContext: GlobalContextImpl,
  val settings: PlatformAnalysisSettings,
  val reuseDataFrom: ProjectResolutionFacade?,
  val moduleFilter: (IdeaModuleInfo) -> Boolean,
  dependencies: List<Any>,
  private val invalidateOnOOCB: Boolean,
  val syntheticFiles: Collection<KtFile> = listOf(),
  val allModules: Collection<IdeaModuleInfo>? = null // null means create resolvers for modules from idea model
) {
    private val cachedValue = CachedValuesManager.getManager(project).createCachedValue(
        {
            val resolverProvider = computeModuleResolverProvider()
            val allDependencies = if (invalidateOnOOCB) {
                resolverForProjectDependencies + KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
            } else {
                resolverForProjectDependencies
            }
            CachedValueProvider.Result.create(resolverProvider, allDependencies)
        },
        /* trackValue = */ false
    )

    private val cachedResolverForProject: ResolverForProject<IdeaModuleInfo>
        get() = globalContext.storageManager.compute { cachedValue.value }

    private val analysisResultsLock = ReentrantLock()
    private val analysisResultsSimpleLock = CancellableSimpleLock(analysisResultsLock,
                                                                  checkCancelled = {
                                                                      ProgressManager.checkCanceled()
                                                                  },
                                                                  interruptedExceptionHandler = { throw ProcessCanceledException(it) })

    private val analysisResults = CachedValuesManager.getManager(project).createCachedValue(
        {
            val resolverForProject = cachedResolverForProject
            val results = object : SLRUCache<KtFile, PerFileAnalysisCache>(2, 3) {
                private val lock = ReentrantLock()

                override fun createValue(file: KtFile): PerFileAnalysisCache {
                    return PerFileAnalysisCache(
                        file,
                        resolverForProject.resolverForModule(file.moduleInfo).componentProvider
                    )
                }

                override fun get(key: KtFile?): PerFileAnalysisCache {
                    lock.lock()
                    try {
                        val cache = super.get(key)
                        if (cache.isValid) {
                            return cache
                        }
                        remove(key)
                        return super.get(key)
                    } finally {
                        lock.unlock()
                    }
                }

                override fun getIfCached(key: KtFile?): PerFileAnalysisCache? {
                    if (lock.tryLock()) {
                        try {
                            return super.getIfCached(key)
                        } finally {
                            lock.unlock()
                        }
                    }
                    return null
                }
            }

            // TODO: why do we need OCB tracker for libs ?
            val allDependencies = resolverForProjectDependencies +
                    KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
            CachedValueProvider.Result.create(results, allDependencies)
        }, false
    )

    private val resolverForProjectDependencies = dependencies + globalContext.exceptionTracker

    private fun computeModuleResolverProvider(): ResolverForProject<IdeaModuleInfo> {
        val delegateResolverForProject: ResolverForProject<IdeaModuleInfo> =
            reuseDataFrom?.cachedResolverForProject ?: EmptyResolverForProject()

        val allModuleInfos = (allModules ?: getModuleInfosFromIdeaModel(project, (settings as? PlatformAnalysisSettingsImpl)?.platform))
            .toMutableSet()

        val syntheticFilesByModule = syntheticFiles.groupBy { it.moduleInfo }
        val syntheticFilesModules = syntheticFilesByModule.keys
        allModuleInfos.addAll(syntheticFilesModules)

        val resolvedModules = allModuleInfos.filter(moduleFilter)
        val resolvedModulesWithDependencies = resolvedModules +
                listOfNotNull(ScriptDependenciesInfo.ForProject.createIfRequired(project, resolvedModules))

        return IdeaResolverForProject(
            resolverDebugName,
            globalContext.withProject(project),
            resolvedModulesWithDependencies,
            syntheticFilesByModule,
            delegateResolverForProject,
            if (invalidateOnOOCB) KotlinModificationTrackerService.getInstance(project).outOfBlockModificationTracker else LibraryModificationTracker.getInstance(project),
            settings
        )
    }

    internal fun resolverForModuleInfo(moduleInfo: IdeaModuleInfo) = cachedResolverForProject.resolverForModule(moduleInfo)

    internal fun resolverForElement(element: PsiElement): ResolverForModule {
        val moduleInfos = mutableSetOf<IdeaModuleInfo>()

        for (result in ModuleInfoProvider.getInstance(element.project).collect(element)) {
            val moduleInfo = result.getOrNull()
            if (moduleInfo != null) {
                val resolver = cachedResolverForProject.tryGetResolverForModule(moduleInfo)
                if (resolver != null) {
                    return resolver
                } else {
                    moduleInfos += moduleInfo
                }
            }

            val error = result.exceptionOrNull()
            if (error != null) {
                LOG.warn("Could not find correct module information", error)
            }
        }

        val containingFile = element.containingFile as? KtFile
        return cachedResolverForProject.tryGetResolverForModule(NotUnderContentRootModuleInfo(project, containingFile))
            ?: cachedResolverForProject.diagnoseUnknownModuleInfo(moduleInfos.toList())
    }

    internal fun resolverForDescriptor(moduleDescriptor: ModuleDescriptor) =
        cachedResolverForProject.resolverForModuleDescriptor(moduleDescriptor)

    internal fun findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo): ModuleDescriptor {
        return cachedResolverForProject.descriptorForModule(ideaModuleInfo)
    }

    internal fun getResolverForProject(): ResolverForProject<IdeaModuleInfo> = cachedResolverForProject

    internal fun getAnalysisResultsForElements(
        elements: Collection<KtElement>,
        callback: DiagnosticSink.DiagnosticsCallback? = null
    ): AnalysisResult {
        assert(elements.isNotEmpty()) { "elements collection should not be empty" }

        val cache = analysisResultsSimpleLock.guarded { analysisResults.value!! }
        val results = elements.map { analysisResultForElement(it, cache, callback) }
        val bindingContext = CompositeBindingContext.create(results.map { it.bindingContext })
        results.firstOrNull { it.isError() }?.let {
            return AnalysisResult.internalError(bindingContext, it.error)
        }

        //TODO: (module refactoring) several elements are passed here in debugger
        return AnalysisResult.success(bindingContext, findModuleDescriptor(elements.first().moduleInfo))
    }

    internal fun getAnalysisResultsForElement(
        element: KtElement,
        callback: DiagnosticSink.DiagnosticsCallback? = null
    ): AnalysisResult {
        val cache = analysisResultsSimpleLock.guarded {
            analysisResults.value!!
        }
        val result = analysisResultForElement(element, cache, callback)
        val bindingContext = result.bindingContext
        result.takeIf { it.isError() }?.let {
            return AnalysisResult.internalError(bindingContext, it.error)
        }

        //TODO: (module refactoring) several elements are passed here in debugger
        return AnalysisResult.success(bindingContext, findModuleDescriptor(element.moduleInfo))
    }

    internal fun fetchAnalysisResultsForElement(element: KtElement): AnalysisResult? {
        val cache: SLRUCache<KtFile, PerFileAnalysisCache>? =
            analysisResultsLock.tryGuarded {
                analysisResults.upToDateOrNull?.get()
            }
        val perFileCache = cache?.getIfCached(element.containingKtFile)
        return perFileCache?.fetchAnalysisResults(element)
    }

    private fun analysisResultForElement(
        element: KtElement,
        cache: SLRUCache<KtFile, PerFileAnalysisCache>,
        callback: DiagnosticSink.DiagnosticsCallback?
    ): AnalysisResult {
        val containingKtFile = element.containingKtFile
        val perFileCache = cache[containingKtFile]
        return try {
            perFileCache.getAnalysisResults(element, callback)
        } catch (e: Throwable) {
            if (e is ControlFlowException) {
                throw e
            }
            val actualCache = analysisResultsSimpleLock.guarded {
                analysisResults.upToDateOrNull?.get()
            }
            if (cache !== actualCache) {
                throw IllegalStateException("Cache has been invalidated during performing analysis for $containingKtFile", e)
            }
            throw e
        }
    }

    override fun toString(): String {
        return "$debugString@${Integer.toHexString(hashCode())}"
    }

    companion object {

        /*
         * Concurrent access to Errors may lead to the class loading dead lock because of non-trivial initialization in Errors.
         * As a work-around, all Error classes are initialized beforehand.
         * It doesn't matter what exact diagnostic factories are used here.
         */
        init {
            consumeFactory(Errors.DEPRECATION)
            consumeFactory(ErrorsJvm.ACCIDENTAL_OVERRIDE)
            consumeFactory(ErrorsJs.CALL_FROM_UMD_MUST_BE_JS_MODULE_AND_JS_NON_MODULE)
            consumeFactory(ErrorsNative.INCOMPATIBLE_THROWS_INHERITED)
        }

        private inline fun consumeFactory(factory: DiagnosticFactory<*>) {
            factory.javaClass
        }
    }
}

const val CHECK_CANCELLATION_PERIOD_MS: Long = 50
inline fun <T> ReentrantLock.tryGuarded(crossinline computable: () -> T): T? =
    if (tryLock(CHECK_CANCELLATION_PERIOD_MS, TimeUnit.MILLISECONDS)) {
        try {
            computable()
        } finally {
            unlock()
        }
    } else {
        null
    }
