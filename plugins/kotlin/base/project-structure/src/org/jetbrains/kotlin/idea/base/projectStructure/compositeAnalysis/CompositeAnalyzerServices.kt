// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.PlatformConfigurator
import org.jetbrains.kotlin.resolve.PlatformConfiguratorBase
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.storage.StorageManager

class CompositeAnalyzerServices(val services: List<PlatformDependentAnalyzerServices>) : PlatformDependentAnalyzerServices() {
    // In case of a common source set with multiple native targets a composite platform may duplicate platformConfigurator; we use a set
    // to deduplicate
    val uniquePlatformConfigurators = services.mapTo(linkedSetOf()) { it.platformConfigurator as PlatformConfiguratorBase }.toList()
    override val platformConfigurator: PlatformConfigurator = CompositePlatformConfigurator(uniquePlatformConfigurators)

    override fun computePlatformSpecificDefaultImports(storageManager: StorageManager, result: MutableList<ImportPath>) {
        val intersectionOfDefaultImports = services.map { service ->
            mutableListOf<ImportPath>()
                .apply { service.computePlatformSpecificDefaultImports(storageManager, this) }
                .toSet()
        }.safeIntersect()

        result.addAll(intersectionOfDefaultImports)
    }

    override val defaultLowPriorityImports: List<ImportPath> = services.map { it.defaultLowPriorityImports.toSet() }.safeIntersect()

    override val excludedImports: List<FqName> = services.map { it.excludedImports.toSet() }.safeUnion()

    private fun <T> List<Set<T>>.safeUnion(): List<T> =
        if (isEmpty()) emptyList() else reduce { first, second -> first.union(second) }.toList()

    private fun <T> List<Set<T>>.safeIntersect(): List<T> =
        if (isEmpty()) emptyList() else reduce { first, second -> first.intersect(second) }.toList()
}