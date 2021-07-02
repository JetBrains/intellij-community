// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPartialPackageNamesIndex
import org.jetbrains.kotlin.idea.vfilefinder.hasSomethingInPackage
import org.jetbrains.kotlin.idea.search.getKotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtFile


object PackageIndexUtil {
    fun getKotlinSubPackageFqNames(
        packageFqName: FqName,
        scope: GlobalSearchScope,
        project: Project,
        targetPlatform: TargetPlatform,
        nameFilter: (Name) -> Boolean
    ) = sequence {
        if (targetPlatform.isJvm()) {
            val javaPackage = JavaPsiFacade.getInstance(project).findPackage(packageFqName.asString())
            if (javaPackage != null) {
                for (psiPackage in javaPackage.getSubPackages(scope)) {
                    val fqName = psiPackage.getKotlinFqName() ?: continue
                    if (nameFilter(fqName.shortName())) {
                        yield(fqName)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun getSubPackageFqNames(
        packageFqName: FqName,
        scope: GlobalSearchScope,
        project: Project,
        nameFilter: (Name) -> Boolean
    ): Collection<FqName> {
        return SubpackagesIndexService.getInstance(project).getSubpackages(packageFqName, scope, nameFilter)
    }

    @JvmStatic
    fun findFilesWithExactPackage(
        packageFqName: FqName,
        searchScope: GlobalSearchScope,
        project: Project
    ): Collection<KtFile> {
        return KotlinExactPackagesIndex.getInstance().get(packageFqName.asString(), project, searchScope)
    }

    @JvmStatic
    fun packageExists(
        packageFqName: FqName,
        searchScope: GlobalSearchScope,
        project: Project
    ): Boolean {

        val subpackagesIndex = SubpackagesIndexService.getInstance(project)
        if (!subpackagesIndex.packageExists(packageFqName)) {
            return false
        }

        return containsFilesWithExactPackage(packageFqName, searchScope, project) ||
                subpackagesIndex.hasSubpackages(packageFqName, searchScope)
    }

    /**
     * Fast check if a [partialFqName] available for specified [searchScope].
     * As [partialFqName] is only some part of entire fqName and therefore could be a false-positive results.
     *
     * But never provides a false-negative results
     * therefore it could be used to define that [partialFqName] is NOT in a [searchScope].
     */
    @JvmStatic
    fun containsFilesWithPartialPackage(
        partialFqName: FqName,
        searchScope: GlobalSearchScope
    ): Boolean = KotlinPartialPackageNamesIndex.hasSomethingInPackage(partialFqName, searchScope)

    @JvmStatic
    fun containsFilesWithExactPackage(
        packageFqName: FqName,
        searchScope: GlobalSearchScope,
        project: Project
    ): Boolean {
        return StubIndex.getInstance().getContainingFiles(
            KotlinExactPackagesIndex.getInstance().key,
            packageFqName.asString(),
            project,
            searchScope
        ).hasNext()
    }
}
