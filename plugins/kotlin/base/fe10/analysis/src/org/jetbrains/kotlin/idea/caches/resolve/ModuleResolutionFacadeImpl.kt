// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.ResolverForProject
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.container.tryGetService
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.project.ResolveElementCache
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.util.application.runWithCancellationCheck
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.AbsentDescriptorHandler
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.lazy.ResolveSession

internal class ModuleResolutionFacadeImpl(
    private val projectFacade: ProjectResolutionFacade,
    private val moduleInfo: IdeaModuleInfo
) : ResolutionFacade, ResolutionFacadeModuleDescriptorProvider {
    override val project: Project
        get() = projectFacade.project

    //TODO: ideally we would like to store moduleDescriptor once and for all
    // but there are some usages that use resolutionFacade and mutate the psi leading to recomputation of underlying structures
    override val moduleDescriptor: ModuleDescriptor
        get() = findModuleDescriptor(moduleInfo)

    override fun findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo) = projectFacade.findModuleDescriptor(ideaModuleInfo)

    override fun analyze(element: KtElement, bodyResolveMode: BodyResolveMode): BindingContext {
        ResolveInDispatchThreadManager.assertNoResolveInDispatchThread()

        if (usePerFileAnalysisCache) {
            fetchWithAllCompilerChecks(element)?.takeUnless { it.isError() }?.let { return it.bindingContext }
        }

        @OptIn(FrontendInternals::class)
        val resolveElementCache = getFrontendService(element, ResolveElementCache::class.java)
        return runWithCancellationCheck {
            resolveElementCache.resolveToElement(element, bodyResolveMode)
        }
    }

    override fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext {
        ResolveInDispatchThreadManager.assertNoResolveInDispatchThread()

        if (elements.isEmpty()) return BindingContext.EMPTY

        if (usePerFileAnalysisCache) {
            elements.singleOrNull()?.let { element ->
                fetchWithAllCompilerChecks(element)?.takeUnless { it.isError() }?.let { return it.bindingContext }
            }
        }

        @OptIn(FrontendInternals::class)
        val resolveElementCache = getFrontendService(elements.first(), ResolveElementCache::class.java)
        return runWithCancellationCheck {
            resolveElementCache.resolveToElements(elements, bodyResolveMode)
        }
    }

    override fun analyzeWithAllCompilerChecks(element: KtElement, callback: DiagnosticSink.DiagnosticsCallback?): AnalysisResult {
        ResolveInDispatchThreadManager.assertNoResolveInDispatchThread()

        return runWithCancellationCheck {
            projectFacade.getAnalysisResultsForElement(element, callback)
        }
    }

    override fun analyzeWithAllCompilerChecks(
        elements: Collection<KtElement>,
        callback: DiagnosticSink.DiagnosticsCallback?
    ): AnalysisResult {
        ResolveInDispatchThreadManager.assertNoResolveInDispatchThread()

        return runWithCancellationCheck {
            projectFacade.getAnalysisResultsForElements(elements, callback)
        }
    }

    override fun fetchWithAllCompilerChecks(element: KtElement): AnalysisResult? {
        ResolveInDispatchThreadManager.assertNoResolveInDispatchThread()

        return runWithCancellationCheck {
            projectFacade.fetchAnalysisResultsForElement(element)
        }
    }

    override fun resolveToDescriptor(declaration: KtDeclaration, bodyResolveMode: BodyResolveMode): DeclarationDescriptor =
        runWithCancellationCheck {
            if (KtPsiUtil.isLocal(declaration)) {
                val bindingContext = analyze(declaration, bodyResolveMode)
                bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration]
                    ?: getFrontendService(moduleInfo, AbsentDescriptorHandler::class.java).diagnoseDescriptorNotFound(declaration)
            } else {
                ResolveInDispatchThreadManager.assertNoResolveInDispatchThread()

                val resolveSession = projectFacade.resolverForElement(declaration).componentProvider.get<ResolveSession>()
                resolveSession.resolveToDescriptor(declaration)
            }
        }

    @FrontendInternals
    override fun <T : Any> getFrontendService(serviceClass: Class<T>): T = getFrontendService(moduleInfo, serviceClass)

    override fun <T : Any> getIdeService(serviceClass: Class<T>): T {
        return projectFacade.resolverForModuleInfo(moduleInfo).componentProvider.create(serviceClass)
    }

    @FrontendInternals
    override fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T {
        return projectFacade.resolverForElement(element).componentProvider.getService(serviceClass)
    }

    @FrontendInternals
    override fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T? {
        return projectFacade.resolverForElement(element).componentProvider.tryGetService(serviceClass)
    }

    private fun <T : Any> getFrontendService(ideaModuleInfo: IdeaModuleInfo, serviceClass: Class<T>): T {
        return projectFacade.resolverForModuleInfo(ideaModuleInfo).componentProvider.getService(serviceClass)
    }

    @FrontendInternals
    override fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T {
        return projectFacade.resolverForDescriptor(moduleDescriptor).componentProvider.getService(serviceClass)
    }

    override fun getResolverForProject(): ResolverForProject<IdeaModuleInfo> {
        return projectFacade.getResolverForProject()
    }

    companion object {
        private val usePerFileAnalysisCache = Registry.`is`("kotlin.resolve.cache.uses.perfile.cache", true)
    }
}

fun ResolutionFacade.findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo): ModuleDescriptor {
    return (this as ResolutionFacadeModuleDescriptorProvider).findModuleDescriptor(ideaModuleInfo)
}


interface ResolutionFacadeModuleDescriptorProvider {
    fun findModuleDescriptor(ideaModuleInfo: IdeaModuleInfo): ModuleDescriptor
}
