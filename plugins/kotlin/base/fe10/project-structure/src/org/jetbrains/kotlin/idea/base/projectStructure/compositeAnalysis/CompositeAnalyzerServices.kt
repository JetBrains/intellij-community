// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DefaultImportsProvider
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.resolve.PlatformConfigurator
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices

class CompositeAnalyzerServices(val services: List<PlatformDependentAnalyzerServices>) : PlatformDependentAnalyzerServices() {
    // In case of a common source set with multiple native targets a composite platform may duplicate platformConfigurator; we use a set
    // to deduplicate
    val uniquePlatformConfigurators: List<PlatformConfigurator> = services.mapTo(linkedSetOf()) { it.platformConfigurator }.toList()
    override val platformConfigurator: PlatformConfigurator = CompositePlatformConfigurator(uniquePlatformConfigurators)

    private fun <T> List<Set<T>>.safeUnion(): List<T> =
        if (isEmpty()) emptyList() else reduce { first, second -> first.union(second) }.toList()

    private fun <T> List<Set<T>>.safeIntersect(): List<T> =
        if (isEmpty()) emptyList() else reduce { first, second -> first.intersect(second) }.toList()

    override val defaultImportsProvider: DefaultImportsProvider by lazy { CompositeDefaultImportsProvider() }

    inner class CompositeDefaultImportsProvider : DefaultImportsProvider() {
        override val defaultLowPriorityImports: List<ImportPath> = services.map { it.defaultImportsProvider.defaultLowPriorityImports.toSet() }.safeIntersect()

        override val excludedImports: List<FqName> = services.map { it.defaultImportsProvider.excludedImports.toSet() }.safeUnion()

        override val platformSpecificDefaultImports: List<ImportPath> = services.map { it.defaultImportsProvider.platformSpecificDefaultImports.toSet() }.safeIntersect()
    }
}
