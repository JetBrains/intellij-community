// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.caches.resolve

import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.StorageComponentContainer
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.frontend.di.createContainerForLazyResolve
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.WasmKlibLibraryInfo
import org.jetbrains.kotlin.idea.project.IdeaAbsentDescriptorHandler
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.wasm.WasmPlatforms
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.SealedClassInheritorsProvider
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.lazy.declarations.DeclarationProviderFactoryService
import org.jetbrains.kotlin.wasm.resolve.WasmPlatformAnalyzerServices

class WasmResolverForModuleFactory(
    private val targetEnvironment: TargetEnvironment
) : ResolverForModuleFactory() {
    override fun <M : ModuleInfo> createResolverForModule(
        moduleDescriptor: ModuleDescriptorImpl,
        moduleContext: ModuleContext,
        moduleContent: ModuleContent<M>,
        resolverForProject: ResolverForProject<M>,
        languageVersionSettings: LanguageVersionSettings,
        sealedInheritorsProvider: SealedClassInheritorsProvider
    ): ResolverForModule {
        val (moduleInfo, syntheticFiles, moduleContentScope) = moduleContent
        val project = moduleContext.project
        val declarationProviderFactory = DeclarationProviderFactoryService.createDeclarationProviderFactory(
            project,
            moduleContext.storageManager,
            syntheticFiles,
            moduleContentScope,
            moduleInfo
        )

        val container = createContainerForLazyResolve(
            moduleContext,
            declarationProviderFactory,
            BindingTraceContext(/* allowSliceRewrite = */ true),
            moduleDescriptor.platform!!,
            WasmPlatformAnalyzerServices,
            targetEnvironment,
            languageVersionSettings,
            IdeaAbsentDescriptorHandler::class.java
        )
        var packageFragmentProvider = container.get<ResolveSession>().packageFragmentProvider

        val libraryProviders = createWasmPackageFragmentProvider(moduleInfo, container, moduleContext, moduleDescriptor)

        if (libraryProviders.isNotEmpty()) {
            packageFragmentProvider = CompositePackageFragmentProvider(
                listOf(packageFragmentProvider) + libraryProviders,
                "CompositeProvider@WasmResolver for $moduleDescriptor"
            )
        }

        return ResolverForModule(packageFragmentProvider, container)
    }
}

internal fun <M : ModuleInfo> createWasmPackageFragmentProvider(
    moduleInfo: M,
    container: StorageComponentContainer,
    moduleContext: ModuleContext,
    moduleDescriptor: ModuleDescriptorImpl
): List<PackageFragmentProvider> = when (moduleInfo) {
    is WasmKlibLibraryInfo -> {
        listOfNotNull(
            WasmPlatforms.Default.idePlatformKind.resolution.createKlibPackageFragmentProvider(
                moduleInfo,
                moduleContext.storageManager,
                container.get(),
                moduleDescriptor
            )
        )
    }
    else -> emptyList()
}
