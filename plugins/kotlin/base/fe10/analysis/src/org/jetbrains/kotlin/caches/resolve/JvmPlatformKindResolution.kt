// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.caches.resolve

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.PlatformAnalysisParameters
import org.jetbrains.kotlin.analyzer.ResolverForModuleFactory
import org.jetbrains.kotlin.analyzer.ResolverForProject
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltIns
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.idea.base.projectStructure.IdeBuiltInsLoadingState
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.IdeaModuleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.LibraryInfo
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.SdkInfo
import org.jetbrains.kotlin.idea.base.projectStructure.supportsAdditionalBuiltInsMembers
import org.jetbrains.kotlin.idea.caches.resolve.BuiltInsCacheKey
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.resolve.TargetEnvironment
import org.jetbrains.kotlin.resolve.jvm.JvmPlatformParameters
import org.jetbrains.kotlin.resolve.jvm.JvmResolverForModuleFactory

private val LOG = Logger.getInstance(JvmPlatformKindResolution::class.java)

class JvmPlatformKindResolution : IdePlatformKindResolution {
    override fun createResolverForModuleFactory(
        settings: PlatformAnalysisParameters,
        environment: TargetEnvironment,
        platform: TargetPlatform
    ): ResolverForModuleFactory {
        return JvmResolverForModuleFactory(settings as JvmPlatformParameters, environment, platform)
    }

    override val kind get() = JvmIdePlatformKind

    override fun getKeyForBuiltIns(moduleInfo: ModuleInfo, sdkInfo: SdkInfo?, stdlibInfo: LibraryInfo?): BuiltInsCacheKey {
        if (IdeBuiltInsLoadingState.isFromClassLoader && stdlibInfo != null) {
            LOG.error("Standard library ${stdlibInfo.displayedName} provided for built-ins, but loading from dependencies is disabled")
        }

        return if (sdkInfo != null)
            CacheKeyByBuiltInsDependencies(sdkInfo.sdk, stdlibInfo)
        else BuiltInsCacheKey.DefaultBuiltInsKey
    }

    override fun createBuiltIns(
      moduleInfo: IdeaModuleInfo,
      projectContext: ProjectContext,
      resolverForProject: ResolverForProject<IdeaModuleInfo>,
      sdkDependency: SdkInfo?,
      stdlibDependency: LibraryInfo?,
    ): KotlinBuiltIns {
        return when {
            sdkDependency == null -> DefaultBuiltIns.Instance
            stdlibDependency == null || moduleInfo is SdkInfo ->
                createBuiltInsFromClassLoader(moduleInfo, projectContext, resolverForProject, sdkDependency)
            else -> createBuiltinsFromModuleDependencies(moduleInfo, projectContext, resolverForProject, sdkDependency, stdlibDependency)
        }
    }

    private fun createBuiltInsFromClassLoader(
      moduleInfo: IdeaModuleInfo,
      projectContext: ProjectContext,
      resolverForProject: ResolverForProject<IdeaModuleInfo>,
      sdkDependency: SdkInfo,
    ): JvmBuiltIns {
        return JvmBuiltIns(projectContext.storageManager, JvmBuiltIns.Kind.FROM_CLASS_LOADER).apply {
            setPostponedSettingsComputation {
                // SDK should be present, otherwise we wouldn't have created JvmBuiltIns in createBuiltIns
                val sdkDescriptor = resolverForProject.descriptorForModule(sdkDependency)

                val isAdditionalBuiltInsFeaturesSupported =
                    moduleInfo.supportsAdditionalBuiltInsMembers(projectContext.project)
                JvmBuiltIns.Settings(sdkDescriptor, isAdditionalBuiltInsFeaturesSupported)
            }
        }
    }

    private fun createBuiltinsFromModuleDependencies(
      moduleInfo: IdeaModuleInfo,
      projectContext: ProjectContext,
      resolverForProject: ResolverForProject<IdeaModuleInfo>,
      sdkDependency: SdkInfo,
      stdlibDependency: LibraryInfo,
    ): JvmBuiltIns {
        if (IdeBuiltInsLoadingState.isFromClassLoader) {
            LOG.error("Incorrect attempt to create built-ins from module dependencies")
        }

        return JvmBuiltIns(projectContext.storageManager, JvmBuiltIns.Kind.FROM_DEPENDENCIES).apply {
            setPostponedBuiltinsModuleComputation {
                val stdlibDescriptor = resolverForProject.descriptorForModule(stdlibDependency)

                stdlibDescriptor as ModuleDescriptorImpl
            }

            setPostponedSettingsComputation {
                val sdkDescriptor = resolverForProject.descriptorForModule(sdkDependency)

                val isAdditionalBuiltInsFeaturesSupported =
                    moduleInfo.supportsAdditionalBuiltInsMembers(projectContext.project)

                JvmBuiltIns.Settings(sdkDescriptor, isAdditionalBuiltInsFeaturesSupported)
            }
        }
    }

    data class CacheKeyByBuiltInsDependencies(val sdk: Sdk, val stdlib: LibraryInfo?) : BuiltInsCacheKey
}
