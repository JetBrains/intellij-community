// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.caches.resolve

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.analyzer.common.CommonAnalysisParameters
import org.jetbrains.kotlin.analyzer.common.configureCommonSpecificComponents
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltInsPackageFragmentProvider
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.*
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.configureModule
import org.jetbrains.kotlin.frontend.di.configureStandardResolveComponents
import org.jetbrains.kotlin.frontend.java.di.configureJavaSpecificComponents
import org.jetbrains.kotlin.frontend.java.di.initializeJavaSpecificComponents
import org.jetbrains.kotlin.idea.base.projectStructure.IdeBuiltInsLoadingState
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.CompositeAnalyzerServices
import org.jetbrains.kotlin.idea.compiler.IdeSealedClassInheritorsProvider
import org.jetbrains.kotlin.idea.project.IdeaAbsentDescriptorHandler
import org.jetbrains.kotlin.idea.project.IdeaEnvironment
import org.jetbrains.kotlin.incremental.components.InlineConstTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolver
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolverImpl
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.platform.*
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.NativePlatforms
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.jvm.JavaDescriptorResolver
import org.jetbrains.kotlin.resolve.jvm.JvmPlatformParameters
import org.jetbrains.kotlin.resolve.jvm.extensions.PackageFragmentProviderExtension
import org.jetbrains.kotlin.resolve.lazy.AbsentDescriptorHandler
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactory
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.kotlin.resolve.scopes.optimization.OptimizingOptions
import org.jetbrains.kotlin.serialization.deserialization.MetadataPackageFragmentProvider
import org.jetbrains.kotlin.serialization.deserialization.MetadataPartProvider
import org.jetbrains.kotlin.storage.StorageManager

class CompositeResolverForModuleFactory(
    private val commonAnalysisParameters: CommonAnalysisParameters,
    private val jvmAnalysisParameters: JvmPlatformParameters,
    private val targetPlatform: TargetPlatform,
    private val compilerServices: CompositeAnalyzerServices
) : ResolverForModuleFactory() {
    override fun <M : ModuleInfo> createResolverForModule(
        moduleDescriptor: ModuleDescriptorImpl,
        moduleContext: ModuleContext,
        moduleContent: ModuleContent<M>,
        resolverForProject: ResolverForProject<M>,
        languageVersionSettings: LanguageVersionSettings,
        sealedInheritorsProvider: SealedClassInheritorsProvider,
        resolveOptimizingOptions: OptimizingOptions?,
        absentDescriptorHandlerClass: Class<out AbsentDescriptorHandler>?
    ): ResolverForModule {
        val (moduleInfo, syntheticFiles, moduleContentScope) = moduleContent
        val project = moduleContext.project
        val declarationProviderFactory = DeclarationProviderFactoryService.createDeclarationProviderFactory(
            project, moduleContext.storageManager, syntheticFiles,
            moduleContentScope,
            moduleInfo
        )

        val metadataPartProvider = commonAnalysisParameters.metadataPartProviderFactory(moduleContent)
        val trace = CodeAnalyzerInitializer.getInstance(project).createTrace()


        val moduleClassResolver = ModuleClassResolverImpl { javaClass ->
            val referencedClassModule = jvmAnalysisParameters.moduleByJavaClass(javaClass)
            // We don't have full control over idea resolve api so we allow for a situation which should not happen in Kotlin.
            // For example, type in a java library can reference a class declared in a source root (is valid but rare case)
            // Providing a fallback strategy in this case can hide future problems, so we should at least log to be able to diagnose those

            @Suppress("UNCHECKED_CAST")
            val resolverForReferencedModule = referencedClassModule?.let { resolverForProject.tryGetResolverForModule(it as M) }

            val resolverForModule = resolverForReferencedModule?.takeIf {
                referencedClassModule.platform.has<JvmPlatform>()
            } ?: run {
                // in case referenced class lies outside of our resolver, resolve the class as if it is inside our module
                // this leads to java class being resolved several times
                resolverForProject.resolverForModule(moduleInfo)
            }
            resolverForModule.componentProvider.get<JavaDescriptorResolver>()
        }

        val packagePartProvider = jvmAnalysisParameters.packagePartProviderFactory(moduleContent)

        val container = createContainerForCompositePlatform(
            moduleContext, moduleContentScope, languageVersionSettings, targetPlatform,
            compilerServices, trace, declarationProviderFactory, metadataPartProvider,
            moduleClassResolver, packagePartProvider
        )

        val packageFragmentProviders = sequence {
            yield(container.get<ResolveSession>().packageFragmentProvider)

            yieldAll(getCommonProvidersIfAny(moduleInfo, moduleContext, moduleDescriptor, container)) // todo: module context
            yieldAll(getJsProvidersIfAny(moduleInfo, moduleContext, moduleDescriptor, container))
            yieldAll(getJvmProvidersIfAny(container))
            yieldAll(getNativeProvidersIfAny(moduleInfo, container))
            yieldAll(getExtensionsProvidersIfAny(moduleInfo, moduleContext, trace))
        }.toList()

        return ResolverForModule(
            CompositePackageFragmentProvider(
                packageFragmentProviders,
                "CompositeProvider@CompositeResolver for $moduleDescriptor"
            ),
            container
        )
    }

    private fun getCommonProvidersIfAny(
        moduleInfo: ModuleInfo,
        moduleContext: ModuleContext,
        moduleDescriptor: ModuleDescriptor,
        container: StorageComponentContainer
    ): List<PackageFragmentProvider> {
        if (!targetPlatform.isCommon()) return emptyList()

        val metadataProvider = container.get<MetadataPackageFragmentProvider>()

        val klibMetadataProvider = CommonPlatforms.defaultCommonPlatform.idePlatformKind.resolution.createKlibPackageFragmentProvider(
            moduleInfo,
            moduleContext.storageManager,
            container.get<LanguageVersionSettings>(),
            moduleDescriptor
        )

        return listOfNotNull(metadataProvider, klibMetadataProvider)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun getJvmProvidersIfAny(container: StorageComponentContainer): List<PackageFragmentProvider> =
        buildList {
            if (targetPlatform.has<JvmPlatform>()) add(container.get<JavaDescriptorResolver>().packageFragmentProvider)

            // Use JVM built-ins only for completely-JVM modules
            addIfNotNull(container.tryGetService(JvmBuiltInsPackageFragmentProvider::class.java))
        }

    private fun getNativeProvidersIfAny(moduleInfo: ModuleInfo, container: StorageComponentContainer): List<PackageFragmentProvider> {
        if (!targetPlatform.has<NativePlatform>()) return emptyList()

        return listOfNotNull(
            NativePlatforms.unspecifiedNativePlatform.idePlatformKind.resolution.createKlibPackageFragmentProvider(
                moduleInfo,
                container.get<StorageManager>(),
                container.get<LanguageVersionSettings>(),
                container.get<ModuleDescriptor>()
            )
        )
    }

    private fun <M : ModuleInfo> getExtensionsProvidersIfAny(
        moduleInfo: M,
        moduleContext: ModuleContext,
        trace: BindingTrace,
    ): Collection<PackageFragmentProvider> = PackageFragmentProviderExtension.getInstances(moduleContext.project)
        .mapNotNull {
            it.getPackageFragmentProvider(
                moduleContext.project,
                moduleContext.module,
                moduleContext.storageManager,
                trace,
                moduleInfo,
                LookupTracker.DO_NOTHING,
            )
        }

    private fun getJsProvidersIfAny(
        moduleInfo: ModuleInfo,
        moduleContext: ModuleContext,
        moduleDescriptor: ModuleDescriptorImpl,
        container: StorageComponentContainer
    ): List<PackageFragmentProvider> {
        if (moduleInfo !is LibraryModuleInfo || !moduleInfo.platform.isJs()) return emptyList()

        return createPackageFragmentProvider(moduleInfo, container, moduleContext, moduleDescriptor)
    }

    private fun createContainerForCompositePlatform(
        moduleContext: ModuleContext,
        moduleContentScope: GlobalSearchScope,
        languageVersionSettings: LanguageVersionSettings,
        targetPlatform: TargetPlatform,
        analyzerServices: CompositeAnalyzerServices,
        trace: BindingTrace,
        declarationProviderFactory: DeclarationProviderFactory,
        metadataPartProvider: MetadataPartProvider,
        // Guaranteed to be non-null for modules with JVM
        moduleClassResolver: ModuleClassResolver?,
        packagePartProvider: PackagePartProvider?
    ): StorageComponentContainer = composeContainer("CompositePlatform") {
        // Shared by all PlatformConfigurators
        configureDefaultCheckers()

        // Specific for each PlatformConfigurator
        for (configurator in analyzerServices.services.map { it.platformConfigurator as PlatformConfiguratorBase }) {
            configurator.configureExtensionsAndCheckers(this)
        }

        // Called by all normal containers set-ups
        configureModule(
            moduleContext,
            targetPlatform,
            analyzerServices,
            trace,
            languageVersionSettings,
            IdeSealedClassInheritorsProvider,
            null,
            IdeaAbsentDescriptorHandler::class.java
        )
        configureStandardResolveComponents()
        useInstance(moduleContentScope)
        useInstance(declarationProviderFactory)
        useInstance(InlineConstTracker.DoNothing)

        // Probably, should be in StandardResolveComponents, but
        useInstance(VirtualFileFinderFactory.getInstance(moduleContext.project).create(moduleContentScope))
        useInstance(packagePartProvider!!)

        // JVM-specific
        if (targetPlatform.has<JvmPlatform>()) {
            configureJavaSpecificComponents(
                moduleContext, moduleClassResolver!!,
                languageVersionSettings,
                configureJavaClassFinder = null,
                javaClassTracker = null,
                useBuiltInsProvider = IdeBuiltInsLoadingState.isFromDependenciesForJvm && targetPlatform.isJvm() // use JVM BuiltIns only for completely JVM modules
            )
        }

        // Common-specific
        if (targetPlatform.isCommon()) {
            configureCommonSpecificComponents()
        }

        IdeaEnvironment.configure(this)
    }.apply {
        if (targetPlatform.has<JvmPlatform>()) {
            initializeJavaSpecificComponents(trace)
        }
    }
}
