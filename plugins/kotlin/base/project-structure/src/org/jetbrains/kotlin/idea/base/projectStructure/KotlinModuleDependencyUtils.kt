// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinModuleDependencyUtils")

package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import org.jetbrains.kotlin.config.KotlinSourceRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.facet.isHMPPEnabled
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.*
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.platform.TargetPlatform

@Service(Service.Level.PROJECT)
class ModuleDependencyCollector(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(ModuleDependencyCollector::class.java)

        fun getInstance(project: Project): ModuleDependencyCollector = project.service()
    }

    fun collectModuleDependencies(
        module: Module,
        platform: TargetPlatform,
        sourceRootType: KotlinSourceRootType,
        includeExportedDependencies: Boolean
    ): Collection<IdeaModuleInfo> {
        val debugInfo = if (LOG.isDebugEnabled) ArrayList<String>() else null

        val orderEnumerator = getOrderEnumerator(module, sourceRootType, includeExportedDependencies)

        val dependencyFilter = when {
            module.isHMPPEnabled -> HmppSourceModuleDependencyFilter(platform)
            else -> NonHmppSourceModuleDependenciesFilter(platform)
        }

        val result = LinkedHashSet<IdeaModuleInfo>()

        orderEnumerator.forEach { orderEntry ->
            if (isApplicable(orderEntry, sourceRootType)) {
                debugInfo?.add("Add entry ${orderEntry.presentableName}")
                for (moduleInfo in collectModuleDependenciesForOrderEntry(orderEntry, sourceRootType)) {
                    if (dependencyFilter.isSupportedDependency(moduleInfo)) {
                        debugInfo?.add("Add module ${moduleInfo.displayedName}")
                        result.add(moduleInfo)
                    }
                }
            } else {
                debugInfo?.add("Skip entry ${orderEntry.presentableName}")
            }

            return@forEach true
        }

        if (debugInfo != null) {
            val debugString = buildString {
                appendLine("Building dependency list for module ${this}")
                appendLine("Platform = ${platform}, isForTests = ${sourceRootType == TestSourceKotlinRootType}")
                debugInfo.joinTo(this, separator = "; ", prefix = "[", postfix = "]")
            }
            LOG.debug(debugString)
        }

        return result
    }

    private fun getOrderEnumerator(
        module: Module,
        sourceRootType: KotlinSourceRootType,
        includeExportedDependencies: Boolean,
    ): OrderEnumerator {
        val rootManager = ModuleRootManager.getInstance(module)

        val dependencyEnumerator = rootManager.orderEntries().compileOnly()
        if (includeExportedDependencies) {
            dependencyEnumerator.recursively().exportedOnly()
        }

        if (sourceRootType == SourceKotlinRootType && module.buildSystemType == BuildSystemType.JPS) {
            dependencyEnumerator.productionOnly()
        }

        return dependencyEnumerator
    }

    private fun isApplicable(orderEntry: OrderEntry, sourceRootType: KotlinSourceRootType): Boolean {
        if (!orderEntry.isValid) {
            return false
        }

        return orderEntry !is ExportableOrderEntry
                || sourceRootType == TestSourceKotlinRootType
                || orderEntry is ModuleOrderEntry && orderEntry.isProductionOnTestDependency
                || orderEntry.scope.isForProductionCompile
    }

    private fun collectModuleDependenciesForOrderEntry(orderEntry: OrderEntry, sourceRootType: KotlinSourceRootType): List<IdeaModuleInfo> {
        fun Module.toInfos() = sourceModuleInfos.filter {
            sourceRootType == TestSourceKotlinRootType || it is ModuleProductionSourceInfo
        }

        return when (orderEntry) {
            is ModuleSourceOrderEntry -> {
                orderEntry.ownerModule.toInfos()
            }

            is ModuleOrderEntry -> {
                val module = orderEntry.module ?: return emptyList()
                if (sourceRootType == SourceKotlinRootType && orderEntry.isProductionOnTestDependency) {
                    listOfNotNull(module.testSourceInfo)
                } else {
                    module.toInfos()
                }
            }

            is LibraryOrderEntry -> {
                val library = orderEntry.library ?: return listOf()
                LibraryInfoCache.getInstance(project)[library]
            }

            is JdkOrderEntry -> {
                val sdk = orderEntry.jdk ?: return listOf()
                listOfNotNull(SdkInfo(project, sdk))
            }

            else -> {
                throw IllegalStateException("Unexpected order entry $orderEntry")
            }
        }
    }
}