// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.caches.resolve

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.PlatformAnalysisParameters
import org.jetbrains.kotlin.analyzer.ResolverForModuleFactory
import org.jetbrains.kotlin.analyzer.ResolverForProject
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.ide.konan.createKlibPackageFragmentProvider
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.JsKlibLibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.caches.resolve.BuiltInsCacheKey
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.library.metadata.impl.KlibMetadataModuleDescriptorFactoryImpl
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.JsIdePlatformKind
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.serialization.js.DynamicTypeDeserializer
import org.jetbrains.kotlin.storage.StorageManager

class JsPlatformKindResolution : IdePlatformKindResolution {
    override val kind get() = JsIdePlatformKind

    override fun getKeyForBuiltIns(moduleInfo: ModuleInfo, sdkInfo: SdkInfo?, stdlibInfo: LibraryInfo?): BuiltInsCacheKey {
        return BuiltInsCacheKey.DefaultBuiltInsKey
    }

    override fun createBuiltIns(
      moduleInfo: IdeaModuleInfo,
      projectContext: ProjectContext,
      resolverForProject: ResolverForProject<IdeaModuleInfo>,
      sdkDependency: SdkInfo?,
      stdlibDependency: LibraryInfo?,
    ): KotlinBuiltIns {
        return DefaultBuiltIns.Instance
    }

    override fun createResolverForModuleFactory(
        settings: PlatformAnalysisParameters,
        environment: TargetEnvironment,
        platform: TargetPlatform
    ): ResolverForModuleFactory = JsResolverForModuleFactory(environment)

    override fun createKlibPackageFragmentProvider(
        moduleInfo: ModuleInfo,
        storageManager: StorageManager,
        languageVersionSettings: LanguageVersionSettings,
        moduleDescriptor: ModuleDescriptor
    ): PackageFragmentProvider? {
        return (moduleInfo as? JsKlibLibraryInfo)
            ?.resolvedKotlinLibrary
            ?.createKlibPackageFragmentProvider(
                storageManager = storageManager,
                metadataModuleDescriptorFactory = metadataModuleDescriptorFactory,
                languageVersionSettings = languageVersionSettings,
                moduleDescriptor = moduleDescriptor,
                lookupTracker = LookupTracker.DO_NOTHING
            )
    }

    companion object {
        private val metadataFactories = KlibMetadataFactories({ DefaultBuiltIns.Instance }, DynamicTypeDeserializer)

        private val metadataModuleDescriptorFactory = KlibMetadataModuleDescriptorFactoryImpl(
            metadataFactories.DefaultDescriptorFactory,
            metadataFactories.DefaultPackageFragmentsFactory,
            metadataFactories.flexibleTypeDeserializer,
        )
    }
}
