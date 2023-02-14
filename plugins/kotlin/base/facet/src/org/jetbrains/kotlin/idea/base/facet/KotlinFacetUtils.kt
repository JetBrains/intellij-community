// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinFacetUtils")

package org.jetbrains.kotlin.idea.base.facet

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetTypeRegistry
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.project.IdeModelsProviderImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jetbrains.kotlin.caches.project.cacheInvalidatingOnRootModifications
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2MetadataCompilerArguments
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.platforms.StableModuleNameProvider
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.psi.NotNullableUserDataProperty
import java.io.File

fun Module.hasKotlinFacet(): Boolean {
    return FacetManager.getInstance(this).getFacetByType(KotlinFacetType.TYPE_ID) != null
}

val Module.externalProjectId: String
    get() = facetSettings?.externalProjectId ?: ""

val Module.kotlinSourceRootType: KotlinSourceRootType?
    get() {
        val isTestModule = facetSettings?.isTestModule ?: return null
        return if (isTestModule) TestSourceKotlinRootType else SourceKotlinRootType
    }

val Module.isMultiPlatformModule: Boolean
    get() = facetSettings?.isMultiPlatformModule ?: false

val Module.isNewMultiPlatformModule: Boolean
    get() {
        // TODO: review clients, correct them to use precise checks for MPP version
        return facetSettings?.mppVersion.isNewMPP || facetSettings?.mppVersion.isHmpp
    }

var Module.isKpmModule: Boolean
        by NotNullableUserDataProperty(Key.create("IS_KPM_MODULE"), false)

val Module.isHMPPEnabled: Boolean
    get() = KotlinFacetSettingsProvider.getInstance(project)?.getInitializedSettings(this)?.mppVersion?.isHmpp ?: false

var Module.refinesFragmentIds: Collection<String>
        by NotNullableUserDataProperty(Key.create("REFINES_FRAGMENT_IDS"), emptyList())

val Module.isTestModule: Boolean
    get() = facetSettings?.isTestModule ?: false

val KotlinFacetSettings.isMultiPlatformModule: Boolean
    get() = mppVersion != null

private val Module.facetSettings: KotlinFacetSettings?
    get() = KotlinFacet.get(this)?.configuration?.settings

@Service(Service.Level.PROJECT)
class ModulesByLinkedKeyCache(private val project: Project) : Disposable, WorkspaceModelChangeListener {
    @Deprecated("Do not use directly", level = DeprecationLevel.ERROR)
    private val cache: MutableMap<String, Module> = HashMap()

    private val lock = Any()

    init {
        LowMemoryWatcher.register(this::invalidate, this)
        project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, this)
    }

    operator fun get(key: String): Module? = useCache { cache ->
        if (cache.isEmpty()) {
            val stableNameProvider = StableModuleNameProvider.getInstance(project)

            val map = runReadAction {
                val modules = ModuleManager.getInstance(project).modules
                modules.associateBy { stableNameProvider.getStableModuleName(it) }
            }
            cache.putAll(map)
        }
        cache[key]
    }

    private fun <T> useCache(block: (MutableMap<String, Module>) -> T?): T? = synchronized(lock) {
        @Suppress("DEPRECATION_ERROR")
        cache.run(block)
    }

    override fun beforeChanged(event: VersionedStorageChange) {
        if (useCache { it.isEmpty() } == true) return

        val storageBefore = event.storageBefore
        val storageAfter = event.storageAfter
        val changes = event.getChanges(ModuleEntity::class.java).ifEmpty { return }

        val stableNameProvider = StableModuleNameProvider.getInstance(project)

        val outdatedModuleNames = changes.asSequence()
            .mapNotNull(EntityChange<ModuleEntity>::oldEntity)
            .mapNotNull { it.findModule(storageBefore) }
            .map(stableNameProvider::getStableModuleName)
            .toList()

        val newModuleNames = changes.asSequence()
            .mapNotNull(EntityChange<ModuleEntity>::newEntity)
            .mapNotNull { it.findModule(storageAfter) }
            .associateBy(stableNameProvider::getStableModuleName)

        useCache { cache ->
            outdatedModuleNames.forEach { cache.remove(it) }
            cache.putAll(newModuleNames)
        }
    }

    private fun invalidate() {
        useCache {
            it.clear()
        }
    }

    override fun dispose() {
        invalidate()
    }

}

val Module.additionalVisibleModules: List<Module>
    get() = cacheInvalidatingOnRootModifications cache@{
        val facetSettings = facetSettings ?: return@cache emptyList()

        val modulesByLinkedKey = project.service<ModulesByLinkedKeyCache>()

        facetSettings.additionalVisibleModuleNames.mapNotNull { moduleName ->
            modulesByLinkedKey[moduleName]
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

        val modulesByLinkedKey = project.service<ModulesByLinkedKeyCache>()
        if (isKpmModule) {
            refinesFragmentIds.mapNotNull { modulesByLinkedKey[it] }
        } else {
            val facetSettings = facetSettings
            when (facetSettings?.mppVersion) {
                null -> emptyList()

                KotlinMultiplatformVersion.M3 -> {
                    facetSettings.dependsOnModuleNames
                        .mapNotNull { modulesByLinkedKey[it] }
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

/**
 * Returns stable binary name of module from the *Kotlin* point of view.
 * Having correct module name is critical for compiler, e.g. for 'internal'-visibility
 * mangling (see KT-23668).
 *
 * Note that build systems and IDEA have their own module systems and, potentially, their
 * names can be different from Kotlin module name (though this is the rare case).
 */
val Module.stableName: Name
    get() {
        // Here we check ideal situation: we have a facet, and it has 'moduleName' argument.
        // This should be the case for the most environments
        val settingsProvider = KotlinFacetSettingsProvider.getInstance(project)
        val explicitNameFromArguments = when (val arguments = settingsProvider?.getInitializedSettings(this)?.mergedCompilerArguments) {
            is K2JVMCompilerArguments -> arguments.moduleName
            is K2JSCompilerArguments -> arguments.outputFile?.let { FileUtil.getNameWithoutExtension(File(it)) }
            is K2MetadataCompilerArguments -> arguments.moduleName
            else -> null // Actually, only 'null' possible here
        }

        // Here we handle pessimistic case: no facet is found or it declares no 'moduleName'
        // We heuristically assume that name of Module in IDEA is the same as Kotlin module (which may be not the case)
        val stableNameApproximation = explicitNameFromArguments ?: name

        return Name.special("<$stableNameApproximation>")
    }

private fun Module.findOldFashionedImplementedModuleNames(): List<String> {
    val facet = FacetManager.getInstance(this).findFacet(
        KotlinFacetType.TYPE_ID,
        FacetTypeRegistry.getInstance().findFacetType(KotlinFacetType.ID)!!.defaultFacetName
    )
    return facet?.configuration?.settings?.implementedModuleNames ?: emptyList()
}

fun Module.externalSystemTestRunTasks(): List<ExternalSystemTestRunTask> {
    return externalSystemRunTasks().filterIsInstance<ExternalSystemTestRunTask>()
}

fun Module.externalSystemNativeMainRunTasks(): List<ExternalSystemNativeMainRunTask> {
    return externalSystemRunTasks().filterIsInstance<ExternalSystemNativeMainRunTask>()
}

private fun Module.externalSystemRunTasks(): List<ExternalSystemRunTask> {
    val settingsProvider = KotlinFacetSettingsProvider.getInstance(project) ?: return emptyList()
    return settingsProvider.getInitializedSettings(this).externalSystemRunTasks
}
