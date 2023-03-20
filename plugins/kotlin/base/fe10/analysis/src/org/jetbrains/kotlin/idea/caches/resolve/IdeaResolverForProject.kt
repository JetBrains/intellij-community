// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.analyzer.common.CommonAnalysisParameters
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.caches.resolve.*
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.idea.base.projectStructure.*
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.CompositeAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.caches.project.*
import org.jetbrains.kotlin.idea.compiler.IdeSealedClassInheritorsProvider
import org.jetbrains.kotlin.idea.project.IdeaAbsentDescriptorHandler
import org.jetbrains.kotlin.idea.project.IdeaEnvironment
import org.jetbrains.kotlin.load.java.structure.JavaClass
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.RESOLUTION_ANCHOR_PROVIDER_CAPABILITY
import org.jetbrains.kotlin.resolve.ResolutionAnchorProvider
import org.jetbrains.kotlin.resolve.STDLIB_CLASS_FINDER_CAPABILITY
import org.jetbrains.kotlin.resolve.jvm.JvmPlatformParameters
import org.jetbrains.kotlin.types.TypeRefinement
import org.jetbrains.kotlin.types.checker.REFINER_CAPABILITY
import org.jetbrains.kotlin.types.checker.Ref
import org.jetbrains.kotlin.types.checker.TypeRefinementSupport
import java.util.*

class IdeaResolverForProject(
    debugName: String,
    projectContext: ProjectContext,
    modules: Collection<IdeaModuleInfo>,
    private val syntheticFilesByModule: Map<IdeaModuleInfo, Collection<KtFile>>,
    delegateResolver: ResolverForProject<IdeaModuleInfo>,
    fallbackModificationTracker: ModificationTracker? = null,

    // Note that 'projectContext.project.useCompositeAnalysis == true' doesn't necessarily imply
    // that 'settings is CompositeAnalysisSettings'. We create "old" settings for some exceptional
    // cases sometimes even when CompositeMode is enabled, see KotlinCacheService.getResolutionFacadeWithForcedPlatform
    private val settings: PlatformAnalysisSettings
) : AbstractResolverForProject<IdeaModuleInfo>(
    debugName,
    projectContext,
    modules,
    fallbackModificationTracker,
    delegateResolver,
    projectContext.project.service<IdePackageOracleFactory>(),
) {

    companion object {
        val PLATFORM_ANALYSIS_SETTINGS = ModuleCapability<PlatformAnalysisSettings>("PlatformAnalysisSettings")
    }

    private val resolutionAnchorProvider = projectContext.project.service<ResolutionAnchorProvider>()
    private val created = Date().toString()

    private val invalidModuleNotifier: InvalidModuleNotifier = object: InvalidModuleNotifier {
        override fun notifyModuleInvalidated(moduleDescriptor: ModuleDescriptor) {
            throw ProcessCanceledException(InvalidModuleException("Accessing invalid module descriptor $moduleDescriptor"))
        }
    }

    private val constantSdkDependencyIfAny: SdkInfo? =
        if (settings is PlatformAnalysisSettingsImpl) settings.sdk?.let { SdkInfo(projectContext.project, it) } else null

    private val builtInsCache: BuiltInsCache =
        (delegateResolver as? IdeaResolverForProject)?.builtInsCache ?: BuiltInsCache(projectContext, this)

    private val stdlibClassFinder = IdeStdlibClassFinderImpl(projectContext.project)

    @OptIn(TypeRefinement::class)
    private fun getRefinerCapability(): Pair<ModuleCapability<Ref<TypeRefinementSupport>>, Ref<TypeRefinementSupport>> {
        val isCompositeAnalysisEnabled = settings is CompositeAnalysisSettings
        val typeRefinementSupport = if (isCompositeAnalysisEnabled) {
            /*
            * Will be properly initialized with a type refiner created by DI container of ResolverForModule.
            * Placeholder is necessary to distinguish state in cases when resolver for module is not created at all.
            * For instance, platform targets with no sources in project.
            */
            TypeRefinementSupport.EnabledUninitialized
        } else {
            TypeRefinementSupport.Disabled
        }
        return REFINER_CAPABILITY to Ref(typeRefinementSupport)
    }

    override fun getAdditionalCapabilities(): Map<ModuleCapability<*>, Any?> {
        return super.getAdditionalCapabilities() +
                getRefinerCapability() +
                (PLATFORM_ANALYSIS_SETTINGS to settings) +
                (RESOLUTION_ANCHOR_PROVIDER_CAPABILITY to resolutionAnchorProvider) +
                (INVALID_MODULE_NOTIFIER_CAPABILITY to invalidModuleNotifier) +
                (STDLIB_CLASS_FINDER_CAPABILITY to stdlibClassFinder)
    }

    override fun sdkDependency(module: IdeaModuleInfo): SdkInfo? {
        if (settings is CompositeAnalysisSettings) {
            require(constantSdkDependencyIfAny == null) { "Shouldn't pass SDK dependency manually for composite analysis mode" }
        }
        return constantSdkDependencyIfAny ?: module.findSdkAcrossDependencies()
    }

    override fun modulesContent(module: IdeaModuleInfo): ModuleContent<IdeaModuleInfo> =
        ModuleContent(module, syntheticFilesByModule[module] ?: emptyList(), module.moduleContentScope)

    override fun builtInsForModule(module: IdeaModuleInfo): KotlinBuiltIns = builtInsCache.getOrCreateIfNeeded(module)

    override fun createResolverForModule(descriptor: ModuleDescriptor, moduleInfo: IdeaModuleInfo): ResolverForModule {
        val moduleContent = ModuleContent(moduleInfo, syntheticFilesByModule[moduleInfo] ?: listOf(), moduleInfo.moduleContentScope)

        val project = projectContext.project
        val languageVersionSettings = project.service<LanguageSettingsProvider>().getLanguageVersionSettings(moduleInfo, project)

        val resolverForModuleFactory = getResolverForModuleFactory(moduleInfo)
        val optimizingOptions = ResolveOptimizingOptionsProvider.getOptimizingOptions(project, descriptor, moduleInfo)

        val resolverForModule = resolverForModuleFactory.createResolverForModule(
            descriptor as ModuleDescriptorImpl,
            projectContext.withModule(descriptor),
            moduleContent,
            this,
            languageVersionSettings,
            sealedInheritorsProvider = IdeSealedClassInheritorsProvider,
            resolveOptimizingOptions = optimizingOptions,
            absentDescriptorHandlerClass = IdeaAbsentDescriptorHandler::class.java
        )
        ResolverForModuleComputationTrackerEx.getInstance(project)?.onCreateResolverForModule(descriptor, moduleInfo)
        return resolverForModule
    }

    private fun getResolverForModuleFactory(moduleInfo: IdeaModuleInfo): ResolverForModuleFactory {
        val platform = moduleInfo.platform

        val jvmPlatformParameters = JvmPlatformParameters(
            packagePartProviderFactory = { IDEPackagePartProvider(it.moduleContentScope) },
            moduleByJavaClass = { javaClass: JavaClass ->
                val psiClass = (javaClass as JavaClassImpl).psi
                when (settings) {
                    is CompositeAnalysisSettings -> psiClass.moduleInfoOrNull
                    else -> psiClass.getPlatformModuleInfo(JvmPlatforms.unspecifiedJvmPlatform)?.platformModule ?: psiClass.moduleInfoOrNull
                }
            },
            resolverForReferencedModule = { targetModuleInfo, referencingModuleInfo ->
                require(targetModuleInfo is IdeaModuleInfo && referencingModuleInfo is IdeaModuleInfo) {
                    "Unexpected modules passed through JvmPlatformParameters to IDE resolver ($targetModuleInfo, $referencingModuleInfo)"
                }
                tryGetResolverForModuleWithResolutionAnchorFallback(targetModuleInfo, referencingModuleInfo)
            },
            useBuiltinsProviderForModule = {
                IdeBuiltInsLoadingState.isFromDependenciesForJvm && it is LibraryInfo && it.isKotlinStdlib(projectContext.project)
            }
        )

        val commonPlatformParameters = CommonAnalysisParameters(
            metadataPartProviderFactory = { IDEPackagePartProvider(it.moduleContentScope) },
            klibMetadataPackageFragmentProviderFactory = { context ->
                CommonPlatforms.defaultCommonPlatform.idePlatformKind.resolution.createKlibPackageFragmentProvider(
                    context.moduleInfo, context.storageManager, context.languageVersionSettings, context.moduleDescriptor
                )
            },
        )

        return if (settings !is CompositeAnalysisSettings) {
            val parameters = when {
                platform.isJvm() -> jvmPlatformParameters
                platform.isCommon() -> commonPlatformParameters
                else -> PlatformAnalysisParameters.Empty
            }

            platform.idePlatformKind.resolution.createResolverForModuleFactory(parameters, IdeaEnvironment, platform)
        } else {
            CompositeResolverForModuleFactory(
                commonPlatformParameters,
                jvmPlatformParameters,
                platform,
                CompositeAnalyzerServices(platform.componentPlatforms.map { it.findAnalyzerServices() })
            )
        }
    }

    // Important: ProjectContext must be from SDK to be sure that we won't run into deadlocks
    class BuiltInsCache(private val projectContextFromSdkResolver: ProjectContext, private val resolverForSdk: IdeaResolverForProject) {
        private val cache = mutableMapOf<BuiltInsCacheKey, KotlinBuiltIns>()

        fun getOrCreateIfNeeded(module: IdeaModuleInfo): KotlinBuiltIns = projectContextFromSdkResolver.storageManager.compute {
            ProgressManager.checkCanceled()

            val sdk = resolverForSdk.sdkDependency(module)
            val stdlib = findStdlibForModulesBuiltins(module)

            val key = module.platform.idePlatformKind.resolution.getKeyForBuiltIns(module, sdk, stdlib)
            val cachedBuiltIns = cache[key]
            if (cachedBuiltIns != null) return@compute cachedBuiltIns

            module.platform.idePlatformKind.resolution
                .createBuiltIns(module, projectContextFromSdkResolver, resolverForSdk, sdk, stdlib)
                .also {
                    // TODO: MemoizedFunction should be used here instead, but for proper we also need a module (for LV settings) that is not contained in the key
                    cache[key] = it
                }
        }

        private fun findStdlibForModulesBuiltins(module: IdeaModuleInfo): LibraryInfo? {
            return when (IdeBuiltInsLoadingState.state) {
                IdeBuiltInsLoadingState.IdeBuiltInsLoading.FROM_CLASSLOADER -> null
                IdeBuiltInsLoadingState.IdeBuiltInsLoading.FROM_DEPENDENCIES_JVM -> {
                    if (module.platform.isJvm()) {
                        module.findJvmStdlibAcrossDependencies()
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun tryGetResolverForModuleWithResolutionAnchorFallback(
        targetModuleInfo: IdeaModuleInfo,
        referencingModuleInfo: IdeaModuleInfo,
    ): ResolverForModule? {
        tryGetResolverForModule(targetModuleInfo)?.let { return it }

        return getResolverForProjectUsingResolutionAnchor(targetModuleInfo, referencingModuleInfo)
    }

    private fun getResolverForProjectUsingResolutionAnchor(
        targetModuleInfo: IdeaModuleInfo,
        referencingModuleInfo: IdeaModuleInfo
    ): ResolverForModule? {
        val moduleDescriptorOfReferencingModule = descriptorByModule[referencingModuleInfo]?.moduleDescriptor
            ?: error("$referencingModuleInfo is not contained in this resolver, which means incorrect use of anchor-aware search")

        val anchorModuleInfo = resolutionAnchorProvider.getResolutionAnchor(moduleDescriptorOfReferencingModule)?.moduleInfo ?: return null

        val resolverForProjectFromAnchorModule = KotlinCacheService.getInstance(projectContext.project)
            .getResolutionFacadeByModuleInfo(anchorModuleInfo, anchorModuleInfo.platform)
            ?.getResolverForProject()
            ?: return null

        require(resolverForProjectFromAnchorModule is IdeaResolverForProject) {
            "Resolution via anchor modules is expected to be used only from IDE resolvers"
        }

        return resolverForProjectFromAnchorModule.tryGetResolverForModule(targetModuleInfo)
    }
}
