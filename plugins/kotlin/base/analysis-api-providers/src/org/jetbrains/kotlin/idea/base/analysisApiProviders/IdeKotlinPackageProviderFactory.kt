// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import org.jetbrains.kotlin.analysis.providers.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.providers.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.providers.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.caches.project.CachedValue
import org.jetbrains.kotlin.caches.project.getValue
import org.jetbrains.kotlin.idea.base.indices.KotlinPackageIndexUtils
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentHashMap

internal class IdeKotlinPackageProviderFactory(private val project: Project) : KotlinPackageProviderFactory() {
    override fun createPackageProvider(searchScope: GlobalSearchScope): KotlinPackageProvider {
        return IdeKotlinPackageProvider(project, searchScope)
    }
}

private class IdeKotlinPackageProvider(project: Project, private val searchScope: GlobalSearchScope) : KotlinPackageProvider() {
    private val cache by CachedValue(project) {
        CachedValueProvider.Result(
            ConcurrentHashMap<FqName, Boolean>(),
            project.createProjectWideOutOfBlockModificationTracker()
        )
    }

    override fun doKotlinPackageExists(packageFqName: FqName): Boolean {
        return cache.getOrPut(packageFqName) { KotlinPackageIndexUtils.packageExists(packageFqName, searchScope) }
    }

    override fun getKotlinSubPackageFqNames(packageFqName: FqName): Set<Name> {
        return KotlinPackageIndexUtils
            .getSubPackageFqNames(packageFqName, searchScope) { true }
            .mapTo(mutableSetOf()) { it.shortName() }
    }
}