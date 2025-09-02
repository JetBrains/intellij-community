// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.ide.konan

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.analysis.decompiler.konan.CachingIdeKlibMetadataLoader
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.functions.functionInterfacePackageFragmentProvider
import org.jetbrains.kotlin.builtins.konan.KonanBuiltIns
import org.jetbrains.kotlin.caches.resolve.IdePlatformKindResolution
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.ide.konan.analyzer.NativeResolverForModuleFactory
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.caches.resolve.BuiltInsCacheKey
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.metadata.DeserializedKlibModuleOrigin
import org.jetbrains.kotlin.library.metadata.KlibMetadataFactories
import org.jetbrains.kotlin.library.metadata.KlibMetadataModuleDescriptorFactory
import org.jetbrains.kotlin.library.metadata.NullFlexibleTypeDeserializer
import org.jetbrains.kotlin.library.metadata.impl.KlibMetadataModuleDescriptorFactoryImpl
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.NativeIdePlatformKind
import org.jetbrains.kotlin.resolve.KlibCompilerDeserializationConfiguration
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.storage.StorageManager

class NativePlatformKindResolution : IdePlatformKindResolution {
    override fun createKlibPackageFragmentProvider(
        moduleInfo: ModuleInfo,
        storageManager: StorageManager,
        languageVersionSettings: LanguageVersionSettings,
        moduleDescriptor: ModuleDescriptor
    ): PackageFragmentProvider? {
        return (moduleInfo as? NativeKlibLibraryInfo)
            ?.resolvedKotlinLibrary
            ?.createKlibPackageFragmentProvider(
                storageManager = storageManager,
                metadataModuleDescriptorFactory = metadataFactories.DefaultDeserializedDescriptorFactory,
                languageVersionSettings = languageVersionSettings,
                moduleDescriptor = moduleDescriptor,
                lookupTracker = LookupTracker.DO_NOTHING
            )
    }

    override fun createResolverForModuleFactory(
        settings: PlatformAnalysisParameters,
        environment: TargetEnvironment,
        platform: TargetPlatform
    ): ResolverForModuleFactory {
        return NativeResolverForModuleFactory(settings, environment, platform)
    }

    override val kind get() = NativeIdePlatformKind

    override fun getKeyForBuiltIns(moduleInfo: ModuleInfo, sdkInfo: SdkInfo?, stdlibInfo: LibraryInfo?): BuiltInsCacheKey = NativeBuiltInsCacheKey

    override fun createBuiltIns(
        moduleInfo: IdeaModuleInfo,
        projectContext: ProjectContext,
        resolverForProject: ResolverForProject<IdeaModuleInfo>,
        sdkDependency: SdkInfo?,
        stdlibDependency: LibraryInfo?,
    ) = createKotlinNativeBuiltIns(moduleInfo, projectContext)

    private fun createKotlinNativeBuiltIns(moduleInfo: ModuleInfo, projectContext: ProjectContext): KotlinBuiltIns {
        val stdlibInfo = moduleInfo.findNativeStdlib() ?: return DefaultBuiltIns.Instance

        val project = projectContext.project
        val storageManager = projectContext.storageManager

        val builtInsModule = metadataFactories.DefaultDescriptorFactory.createDescriptorAndNewBuiltIns(
            KotlinBuiltIns.BUILTINS_MODULE_NAME,
            storageManager,
            DeserializedKlibModuleOrigin(stdlibInfo.resolvedKotlinLibrary),
            stdlibInfo.capabilities
        )

        val languageVersionSettings = project.service<LanguageSettingsProvider>().getLanguageVersionSettings(stdlibInfo, project)

        val stdlibPackageFragmentProvider = createKlibPackageFragmentProvider(
            stdlibInfo,
            storageManager,
            languageVersionSettings,
            builtInsModule
        ) ?: return DefaultBuiltIns.Instance

        builtInsModule.initialize(
            CompositePackageFragmentProvider(
                listOf(
                    stdlibPackageFragmentProvider,
                    functionInterfacePackageFragmentProvider(storageManager, builtInsModule),
                    (metadataFactories.DefaultDeserializedDescriptorFactory as KlibMetadataModuleDescriptorFactoryImpl)
                        .createForwardDeclarationHackPackagePartProvider(storageManager, builtInsModule)
                ),
                "CompositeProvider@NativeBuiltins for $builtInsModule"
            )
        )

        builtInsModule.setDependencies(listOf(builtInsModule))

        return builtInsModule.builtIns
    }

    object NativeBuiltInsCacheKey : BuiltInsCacheKey
}

private val metadataFactories = KlibMetadataFactories(::KonanBuiltIns, NullFlexibleTypeDeserializer)

private fun ModuleInfo.findNativeStdlib(): NativeKlibLibraryInfo? =
    dependencies().lazyClosure { it.dependencies() }
        .filterIsInstance<NativeKlibLibraryInfo>()
        .firstOrNull { it.isStdlib && it.compatibilityInfo.isCompatible }

internal fun KotlinLibrary.createKlibPackageFragmentProvider(
    storageManager: StorageManager,
    metadataModuleDescriptorFactory: KlibMetadataModuleDescriptorFactory,
    languageVersionSettings: LanguageVersionSettings,
    moduleDescriptor: ModuleDescriptor,
    lookupTracker: LookupTracker
): PackageFragmentProvider? {
    if (!compatibilityInfo.isCompatible) return null

    val packageFragmentNames = CachingIdeKlibMetadataLoader.loadModuleHeader(this).packageFragmentNameList

    return metadataModuleDescriptorFactory.createPackageFragmentProvider(
        library = this,
        packageAccessHandler = CachingIdeKlibMetadataLoader,
        packageFragmentNames = packageFragmentNames,
        storageManager = storageManager,
        moduleDescriptor = moduleDescriptor,
        configuration = KlibCompilerDeserializationConfiguration(languageVersionSettings),
        compositePackageFragmentAddend = null,
        lookupTracker = lookupTracker
    )
}

/**
 * @see [org.jetbrains.kotlin.utils.closure].
 */
private fun <T> Collection<T>.lazyClosure(f: (T) -> Collection<T>): Sequence<T> = sequence {
    if (isEmpty()) return@sequence
    var sizeBeforeIteration = 0

    yieldAll(this@lazyClosure)
    var yieldedCount = size
    var elementsToCheck = this@lazyClosure

    while (yieldedCount > sizeBeforeIteration) {
        val toAdd = hashSetOf<T>()
        elementsToCheck.forEach {
            val neighbours = f(it)
            yieldAll(neighbours)
            yieldedCount += neighbours.size
            toAdd.addAll(neighbours)
        }
        elementsToCheck = toAdd
        sizeBeforeIteration = yieldedCount
    }
}
