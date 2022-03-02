// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.caches.project

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.getBuildSystemType
import org.jetbrains.kotlin.idea.project.isHMPPEnabled
import org.jetbrains.kotlin.platform.TargetPlatform

fun Module.getSourceModuleDependencies(
    forProduction: Boolean,
    platform: TargetPlatform,
    includeTransitiveDependencies: Boolean = true,
): List<IdeaModuleInfo> {
    // Use StringBuilder so that all lines are written into the log atomically (otherwise
    // logs of call to getIdeaModelDependencies for several different modules interleave, leading
    // to unreadable mess)
    val debugString: StringBuilder? = if (LOG.isDebugEnabled) StringBuilder() else null
    debugString?.appendLine("Building idea model dependencies for module ${this}, platform=${platform}, forProduction=$forProduction")

    val allIdeaModuleInfoDependencies = resolveDependenciesFromOrderEntries(debugString, forProduction, includeTransitiveDependencies)
    val supportedModuleInfoDependencies = filterSourceModuleDependencies(debugString, platform, allIdeaModuleInfoDependencies)

    LOG.debug(debugString?.toString())

    return supportedModuleInfoDependencies.toList()
}

private fun Module.resolveDependenciesFromOrderEntries(
    debugString: StringBuilder?,
    forProduction: Boolean,
    includeTransitiveDependencies: Boolean,
): Set<IdeaModuleInfo> {

    //NOTE: lib dependencies can be processed several times during recursive traversal
    val result = LinkedHashSet<IdeaModuleInfo>()
    val dependencyEnumerator = ModuleRootManager.getInstance(this).orderEntries().compileOnly()
    if (includeTransitiveDependencies) {
        dependencyEnumerator.recursively().exportedOnly()
    }
    if (forProduction && getBuildSystemType() == BuildSystemType.JPS) {
        dependencyEnumerator.productionOnly()
    }

    debugString?.append("    IDEA dependencies: [")
    dependencyEnumerator.forEach { orderEntry ->
        debugString?.append("${orderEntry.presentableName} ")
        if (orderEntry.acceptAsDependency(forProduction)) {
            result.addAll(orderEntryToModuleInfo(project, orderEntry, forProduction))
            debugString?.append("OK; ")
        } else {
            debugString?.append("SKIP; ")
        }
        true
    }
    debugString?.appendLine("]")

    return result.toSet()
}

private fun Module.filterSourceModuleDependencies(
    debugString: StringBuilder?,
    platform: TargetPlatform,
    dependencies: Set<IdeaModuleInfo>
): Set<IdeaModuleInfo> {

    val dependencyFilter = if (isHMPPEnabled) HmppSourceModuleDependencyFilter(platform) else NonHmppSourceModuleDependenciesFilter(platform)
    val supportedDependencies = dependencies.filter { dependency -> dependencyFilter.isSupportedDependency(dependency) }.toSet()

    debugString?.appendLine(
        "    Corrected result (Supported dependencies): ${
            supportedDependencies.joinToString(
                prefix = "[",
                postfix = "]",
                separator = ";"
            ) { it.displayedName }
        }"
    )

    return supportedDependencies
}

private fun OrderEntry.acceptAsDependency(forProduction: Boolean): Boolean {
    return this !is ExportableOrderEntry
            || !forProduction
            // this is needed for Maven/Gradle projects with "production-on-test" dependency
            || this is ModuleOrderEntry && isProductionOnTestDependency
            || scope.isForProductionCompile
}

private fun orderEntryToModuleInfo(project: Project, orderEntry: OrderEntry, forProduction: Boolean): List<IdeaModuleInfo> {
    fun Module.toInfos() = correspondingModuleInfos().filter { !forProduction || it is ModuleProductionSourceInfo }

    if (!orderEntry.isValid) return emptyList()

    return when (orderEntry) {
        is ModuleSourceOrderEntry -> {
            orderEntry.getOwnerModule().toInfos()
        }
        is ModuleOrderEntry -> {
            val module = orderEntry.module ?: return emptyList()
            if (forProduction && orderEntry.isProductionOnTestDependency) {
                listOfNotNull(module.testSourceInfo())
            } else {
                module.toInfos()
            }
        }
        is LibraryOrderEntry -> {
            val library = orderEntry.library ?: return listOf()
            createLibraryInfo(project, library)
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
