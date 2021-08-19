// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.fir.low.level.api.ide

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.fir.low.level.api.KtPackageProvider
import org.jetbrains.kotlin.idea.fir.low.level.api.KtPackageProviderFactory
import org.jetbrains.kotlin.idea.fir.low.level.api.api.createProjectWideOutOfBlockModificationTracker
import org.jetbrains.kotlin.idea.stubindex.PackageIndexUtil
import org.jetbrains.kotlin.idea.util.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.concurrent.ConcurrentHashMap

internal class KtPackageProviderIdeImpl(
    private val project: Project,
    private val searchScope: GlobalSearchScope,
) : KtPackageProvider() {

    private val cache by cachedValue(
        project,
        project.createProjectWideOutOfBlockModificationTracker()
    ) { ConcurrentHashMap<FqName, Boolean>() }

    override fun isPackageExists(packageFqName: FqName): Boolean {
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

class KtPackageProviderFactoryIdeImpl(private val project: Project) : KtPackageProviderFactory() {
    override fun createPackageProvider(searchScope: GlobalSearchScope): KtPackageProvider {
        return KtPackageProviderIdeImpl(project, searchScope)
    }
}