// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.analysis.providers.ide

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.providers.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.providers.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.providers.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentHashMap

internal class KotlinIdePackageProviderFactory(private val project: Project) : KotlinPackageProviderFactory() {
    override fun createPackageProvider(searchScope: GlobalSearchScope): KotlinPackageProvider {
        return KotlinPackageProviderIdeImpl(project, searchScope)
    }
}

private class KotlinPackageProviderIdeImpl(
    private val project: Project,
    private val searchScope: GlobalSearchScope,
) : KotlinPackageProvider() {

    private val cache by cachedValue(
        project,
        project.createProjectWideOutOfBlockModificationTracker()
    ) { ConcurrentHashMap<FqName, Boolean>() }

    override fun doKotlinPackageExists(packageFqName: FqName): Boolean {
        return cache[packageFqName] ?: PackageIndexUtil.packageExists(packageFqName, searchScope, project).also {
            cache.putIfAbsent(packageFqName, it)
        }
    }

    override fun getKotlinSubPackageFqNames(packageFqName: FqName): Set<Name> {
        return PackageIndexUtil
            .getSubPackageFqNames(packageFqName, searchScope, project) { true }
            .mapTo(mutableSetOf()) { it.shortName() }
    }
}

