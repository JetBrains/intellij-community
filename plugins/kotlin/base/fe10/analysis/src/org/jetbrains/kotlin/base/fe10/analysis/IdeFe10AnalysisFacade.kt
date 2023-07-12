// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fe10.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade
import org.jetbrains.kotlin.analysis.api.descriptors.Fe10AnalysisFacade.AnalysisMode
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbolOrigin
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.psi.KtElement
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

@OptIn(FrontendInternals::class)
internal class IdeFe10AnalysisFacade(private val project: Project): Fe10AnalysisFacade {
    private inline fun <reified T: Any> getFe10Service(element: KtElement): T {
        return element.getResolutionFacade().getFrontendService(T::class.java)
    }

    override fun getResolveSession(element: KtElement): ResolveSession = getFe10Service(element)
    override fun getDeprecationResolver(element: KtElement): DeprecationResolver = getFe10Service(element)
    override fun getCallResolver(element: KtElement): CallResolver = getFe10Service(element)
    override fun getKotlinToResolvedCallTransformer(element: KtElement): KotlinToResolvedCallTransformer = getFe10Service(element)
    override fun getKotlinTypeRefiner(element: KtElement): KotlinTypeRefiner = getFe10Service(element)

    override fun getOverloadingConflictResolver(element: KtElement): OverloadingConflictResolver<ResolvedCall<*>> {
        val resolutionFacade = element.getResolutionFacade()
        val moduleDescriptor = resolutionFacade.moduleDescriptor

        return createOverloadingConflictResolver(
            moduleDescriptor.builtIns,
            moduleDescriptor,
            resolutionFacade.getFrontendService(TypeSpecificityComparator::class.java),
            resolutionFacade.getFrontendService(PlatformOverloadsSpecificityComparator::class.java),
            resolutionFacade.getFrontendService(CancellationChecker::class.java),
            resolutionFacade.getFrontendService(KotlinTypeRefiner::class.java)
        )
    }

    override fun analyze(elements: List<KtElement>, mode: AnalysisMode): BindingContext {
        val bodyResolveMode = when (mode) {
            AnalysisMode.FULL -> BodyResolveMode.FULL
            AnalysisMode.PARTIAL_WITH_DIAGNOSTICS -> BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS
            AnalysisMode.PARTIAL -> BodyResolveMode.PARTIAL
        }

        val resolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacade(elements)
        return resolutionFacade.analyze(elements, bodyResolveMode)
    }

    override fun getOrigin(file: VirtualFile): KtSymbolOrigin {
        return KtSymbolOrigin.SOURCE
    }
}