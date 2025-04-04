// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fe10.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisContext
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade.AnalysisMode
import org.jetbrains.kotlin.analysis.api.lifetime.KaLifetimeToken
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.util.K1ModeProjectStructureApi
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.CallResolver
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.results.OverloadingConflictResolver
import org.jetbrains.kotlin.resolve.calls.results.PlatformOverloadsSpecificityComparator
import org.jetbrains.kotlin.resolve.calls.results.TypeSpecificityComparator
import org.jetbrains.kotlin.resolve.calls.results.createOverloadingConflictResolver
import org.jetbrains.kotlin.resolve.calls.tower.KotlinToResolvedCallTransformer
import org.jetbrains.kotlin.resolve.deprecation.DeprecationResolver
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.util.CancellationChecker

@OptIn(FrontendInternals::class, KaPlatformInterface::class)
internal class IdeFe10AnalysisFacade(private val project: Project) : Fe10AnalysisFacade {

    @OptIn(K1ModeProjectStructureApi::class)
    override fun getAnalysisContext(ktModule: KaModule, token: KaLifetimeToken): Fe10AnalysisContext {
        val moduleInfo = ktModule.moduleInfo
        val resolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacadeByModuleInfo(moduleInfo, ktModule.targetPlatform)
        return resolutionFacade.getAnalysisContext(token)
    }

    override fun getAnalysisContext(element: KtElement, token: KaLifetimeToken): Fe10AnalysisContext {
        val resolutionFacade = element.getResolutionFacade()
        return resolutionFacade.getAnalysisContext(token)
    }

    private fun ResolutionFacade.getAnalysisContext(token: KaLifetimeToken): Fe10AnalysisContext {
        return Fe10AnalysisContext(
            facade = this@IdeFe10AnalysisFacade,
            resolveSession = getFrontendService(ResolveSession::class.java),
            deprecationResolver = getFrontendService(DeprecationResolver::class.java),
            callResolver = getFrontendService<CallResolver>(CallResolver::class.java),
            kotlinToResolvedCallTransformer = getFrontendService(KotlinToResolvedCallTransformer::class.java),
            overloadingConflictResolver = getOverloadingConflictResolver(),
            kotlinTypeRefiner = getFrontendService(KotlinTypeRefiner::class.java),
            token = token,
        )
    }

    private fun ResolutionFacade.getOverloadingConflictResolver(): OverloadingConflictResolver<ResolvedCall<*>> {
        val moduleDescriptor = moduleDescriptor

        return createOverloadingConflictResolver(
            moduleDescriptor.builtIns,
            moduleDescriptor,
            getFrontendService(TypeSpecificityComparator::class.java),
            getFrontendService(PlatformOverloadsSpecificityComparator::class.java),
            getFrontendService(CancellationChecker::class.java),
            getFrontendService(KotlinTypeRefiner::class.java),
        )
    }

    override fun analyze(elements: List<KtElement>, mode: AnalysisMode): BindingContext {
        val resolutionFacade = getResolutionFacade(elements) ?: return BindingContext.EMPTY

        if (mode == AnalysisMode.ALL_COMPILER_CHECKS) {
            return resolutionFacade.analyzeWithAllCompilerChecks(elements).bindingContext
        }

        @Suppress("KotlinConstantConditions")
        val bodyResolveMode = when (mode) {
            AnalysisMode.FULL -> BodyResolveMode.FULL
            AnalysisMode.PARTIAL_WITH_DIAGNOSTICS -> BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS
            AnalysisMode.PARTIAL -> BodyResolveMode.PARTIAL
            AnalysisMode.ALL_COMPILER_CHECKS -> error("Must have been handled above")
        }

        return resolutionFacade.analyze(elements, bodyResolveMode)
    }

    private fun getResolutionFacade(elements: List<KtElement>): ResolutionFacade? {
        val kotlinCacheService = KotlinCacheService.getInstance(project)

        if (elements.size == 1) {
            return kotlinCacheService.getResolutionFacade(elements.single())
        }

        val files = elements
            .asSequence()
            .mapNotNull { it.containingFile }
            .mapNotNull { if (it is KtCodeFragment) it.context?.containingFile else it }
            .filterIsInstance<KtFile>()
            .distinct()
            .toList()

        if (files.isEmpty()) {
            return null
        }

        val scriptFiles = files.filter { it.isScript() }
        if (scriptFiles.isNotEmpty()) {
            return kotlinCacheService.getResolutionFacade(scriptFiles)
        }

        // The following logic is taken from 'KotlinCacheServiceImpl.filterNotInProjectSource()'
        val moduleInfo = files.first().moduleInfo
        val specialFiles = files
            .filterNot { RootKindFilter.projectSources.matches(it) && moduleInfo.contentScope.contains(it.virtualFile) }

        if (specialFiles.isNotEmpty()) {
            // 'KotlinCacheServiceImpl' is bad in getting a resolution facade for multiple files outside the so-called 'main' module root.
            // Here we artificially choose the facade for the first element. It's not quite correct, still it's better than nothing.
            return kotlinCacheService.getResolutionFacade(specialFiles.first())
        }

        return kotlinCacheService.getResolutionFacade(files)
    }

    override fun getOrigin(file: VirtualFile): KaSymbolOrigin {
        return KaSymbolOrigin.SOURCE
    }
}