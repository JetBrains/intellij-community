// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinFacetConfigurationUtils")
package org.jetbrains.kotlin.idea.base.facet

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetTypeRegistry
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.Key
import org.jetbrains.kotlin.base.util.isAndroidModule
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.platforms.StableModuleNameProvider
import org.jetbrains.kotlin.idea.caches.project.SourceType
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty

// FIXME(dsavvinov): this logic is clearly wrong in MPP environment; review and fix
val Project.platform: TargetPlatform?
    get() {
        val jvmTarget = Kotlin2JvmCompilerArgumentsHolder.getInstance(this).settings.jvmTarget ?: return null
        val version = JvmTarget.fromString(jvmTarget) ?: return null
        return JvmPlatforms.jvmPlatformByTargetVersion(version)
    }

val Module.platform: TargetPlatform?
    get() = KotlinFacetSettingsProvider.getInstance(project)?.getInitializedSettings(this)?.targetPlatform ?: project.platform

val Module.externalProjectId: String
    get() = facetSettings?.externalProjectId ?: ""

val Module.sourceType: SourceType?
    get() = facetSettings?.isTestModule?.let { isTest -> if (isTest) SourceType.TEST else SourceType.PRODUCTION }

val Module.isMultiPlatformModule: Boolean
    get() = facetSettings?.isMultiPlatformModule ?: false

val Module.isNewMultiPlatformModule: Boolean
    get() {
        // TODO: review clients, correct them to use precise checks for MPP version
        return facetSettings?.mppVersion.isNewMPP || facetSettings?.mppVersion.isHmpp
    }

var Module.isKpmModule: Boolean
        by NotNullableUserDataProperty(Key.create("IS_KPM_MODULE"), false)

var Module.refinesFragmentIds: Collection<String>
        by NotNullableUserDataProperty(Key.create("REFINES_FRAGMENT_IDS"), emptyList())

val Module.isTestModule: Boolean
    get() = facetSettings?.isTestModule ?: false

val KotlinFacetSettings.isMultiPlatformModule: Boolean
    get() = mppVersion != null

private val Module.facetSettings: KotlinFacetSettings?
    get() = KotlinFacet.get(this)?.configuration?.settings

private val Project.modulesByLinkedKey: Map<String, Module>
    get() = cacheInvalidatingOnRootModifications {
        val moduleManager = ModuleManager.getInstance(this)
        val stableNameProvider = StableModuleNameProvider.getInstance(this)

        moduleManager.modules.associateBy { stableNameProvider.getStableModuleName(it) }
    }

val Module.additionalVisibleModules: List<Module>
    get() = cacheInvalidatingOnRootModifications cache@ {
        val facetSettings = facetSettings ?: return@cache emptyList()
        facetSettings.additionalVisibleModuleNames.mapNotNull { moduleName ->
            project.modulesByLinkedKey[moduleName]
        }
    }

val Module.implementedModules: List<Module>
    get() = cacheInvalidatingOnRootModifications {
        fun Module.implementedModulesM2(): List<Module> {
            return rootManager.dependencies.filter {
                // TODO: remove additional android check
                it.isNewMultiPlatformModule &&
                        it.platform.isCommon() &&
                        it.externalProjectId == externalProjectId &&
                        (isAndroidModule() || it.isTestModule == isTestModule)
            }
        }

        if (isKpmModule) {
            refinesFragmentIds.mapNotNull { project.modulesByLinkedKey[it] }
        } else {
            val facetSettings = facetSettings
            when (facetSettings?.mppVersion) {
                null -> emptyList()

                KotlinMultiplatformVersion.M3 -> {
                    facetSettings.dependsOnModuleNames
                        .mapNotNull { project.modulesByLinkedKey[it] }
                        // HACK: we do not import proper dependsOn for android source-sets in M3, so fallback to M2-impl
                        // to at least not make things worse.
                        // See KT-33809 for details
                        .plus(if (isAndroidModule()) implementedModulesM2() else emptyList())
                        .distinct()
                }

                KotlinMultiplatformVersion.M2 -> {
                    implementedModulesM2()
                }

                KotlinMultiplatformVersion.M1 -> {
                    val modelsProvider = IdeModelsProviderImpl(project)
                    findOldFashionedImplementedModuleNames().mapNotNull { modelsProvider.findIdeModule(it) }
                }
            }
        }
    }

val Module.implementingModules: List<Module>
    get() = cacheInvalidatingOnRootModifications {
        fun Module.implementingModulesM2(moduleManager: ModuleManager): List<Module> {
            return moduleManager.getModuleDependentModules(this).filter {
                it.isNewMultiPlatformModule && it.externalProjectId == externalProjectId
            }
        }

        val moduleManager = ModuleManager.getInstance(project)
        val stableNameProvider = StableModuleNameProvider.getInstance(project)

        if (isKpmModule) {
            moduleManager.modules.filter { stableNameProvider.getStableModuleName(this) in it.refinesFragmentIds }
        } else when (facetSettings?.mppVersion) {
            null -> emptyList()

            KotlinMultiplatformVersion.M3 -> {
                val thisModuleStableName = stableNameProvider.getStableModuleName(this)
                val result = mutableSetOf<Module>()
                moduleManager.modules.filterTo(result) { it.facetSettings?.dependsOnModuleNames?.contains(thisModuleStableName) == true }

                // HACK: we do not import proper dependsOn for android source-sets in M3,
                // so add all Android modules that M2-implemention would've added,
                // to at least not make things worse.
                // See KT-33809 for details
                implementingModulesM2(moduleManager).forEach { if (it !in result && it.isAndroidModule()) result += it }

                result.toList()
            }

            KotlinMultiplatformVersion.M2 -> implementingModulesM2(moduleManager)

            KotlinMultiplatformVersion.M1 -> moduleManager.modules.filter { name in it.findOldFashionedImplementedModuleNames() }
        }
    }

private fun Module.findOldFashionedImplementedModuleNames(): List<String> {
    val facet = FacetManager.getInstance(this).findFacet(
        KotlinFacetType.TYPE_ID,
        FacetTypeRegistry.getInstance().findFacetType(KotlinFacetType.ID)!!.defaultFacetName
    )
    return facet?.configuration?.settings?.implementedModuleNames ?: emptyList()
}