// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import org.jetbrains.kotlin.analysis.api.platform.caches.getOrPut
import org.jetbrains.kotlin.analysis.api.platform.mergeSpecificProviders
import org.jetbrains.kotlin.analysis.api.platform.packages.*
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KaGlobalSearchScopeMerger
import org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.KotlinRestrictedAnalysisService
import org.jetbrains.kotlin.analysis.api.platform.restrictedAnalysis.withRestrictedDataAccess
import org.jetbrains.kotlin.caches.project.CachedValue
import org.jetbrains.kotlin.caches.project.getValue
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentHashMap

internal class IdeKotlinPackageProviderFactory(private val project: Project) : KotlinCachingPackageProviderFactory(project) {
    override fun createNewPackageProvider(searchScope: GlobalSearchScope): KotlinPackageProvider =
        IdeKotlinPackageProvider(project, searchScope)
}

internal class IdeKotlinPackageProviderMerger(private val project: Project) : KotlinPackageProviderMerger {
    override fun merge(providers: List<KotlinPackageProvider>): KotlinPackageProvider =
        providers.mergeSpecificProviders<_, IdeKotlinPackageProvider>(KotlinCompositePackageProvider.factory) { targetProviders ->
            val combinedScope = KaGlobalSearchScopeMerger.getInstance(project).union(targetProviders.map { it.searchScope })
            project.createPackageProvider(combinedScope)
        }
}

private class IdeKotlinPackageProvider(
    project: Project,
    searchScope: GlobalSearchScope
) : KotlinPackageProviderBase(project, searchScope) {
    private val restrictedAnalysisService by lazy(LazyThreadSafetyMode.PUBLICATION) {
        KotlinRestrictedAnalysisService.getInstance(project)
    }

    /**
     * We don't need to invalidate the cache because [KotlinPackageProvider]'s lifetime is already constrained by modification. The cached
     * value is still useful to keep the cache behind a soft reference.
     */
    private val packageExistsCache by CachedValue(project) {
        CachedValueProvider.Result(
            ConcurrentHashMap<FqName, Boolean>(),
            ModificationTracker.NEVER_CHANGED,
        )
    }

    private val subpackageNamesCache =
        Caffeine.newBuilder()
            .maximumSize(250)
            .build<FqName, Set<Name>>()

    override fun doesKotlinOnlyPackageExist(packageFqName: FqName): Boolean {
        return packageExistsCache.getOrPut(packageFqName) {
            restrictedAnalysisService.withRestrictedDataAccess {
                KotlinPackageIndexUtils.packageExists(packageFqName, searchScope)
            }
        }
    }

    override fun getKotlinOnlySubpackageNames(packageFqName: FqName): Set<Name> {
        if (packageExistsCache[packageFqName] == false) return emptySet()

        return subpackageNamesCache.getOrPut(packageFqName) { packageFqName ->
            restrictedAnalysisService.withRestrictedDataAccess {
                KotlinPackageIndexUtils.getSubpackageNames(packageFqName, searchScope)
            }
        } ?: emptySet()
    }
}
