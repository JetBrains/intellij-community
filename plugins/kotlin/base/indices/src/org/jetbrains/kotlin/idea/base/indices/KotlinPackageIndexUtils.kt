// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.indices

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinExactPackagesIndex
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPartialPackageNamesIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile

object KotlinPackageIndexUtils {
    fun getSubPackageFqNames(
        packageFqName: FqName,
        scope: GlobalSearchScope,
        nameFilter: (Name) -> Boolean
    ): Collection<FqName> = getSubpackages(packageFqName, scope, nameFilter)

    fun findFilesWithExactPackage(
        packageFqName: FqName,
        searchScope: GlobalSearchScope,
        project: Project
    ): Collection<KtFile> = KotlinExactPackagesIndex.get(packageFqName.asString(), project, searchScope)

    /**
     * Return true if exists package with exact [fqName] OR there are some subpackages of [fqName]
     */
    fun packageExists(fqName: FqName, project: Project): Boolean =
        packageExists(fqName, GlobalSearchScope.allScope(project))

    /**
     * Return true if package [packageFqName] exists or some subpackages of [packageFqName] exist in [searchScope]
     */
    fun packageExists(
        packageFqName: FqName,
        searchScope: GlobalSearchScope
    ): Boolean = !FileBasedIndex.getInstance().processValues(
        KotlinPartialPackageNamesIndex.NAME,
        packageFqName,
        null,
        FileBasedIndex.ValueProcessor { _, _ -> false },
        searchScope
    )

    /**
     * Return all direct subpackages of package [fqName].
     *
     * I.e. if there are packages `a.b`, `a.b.c`, `a.c`, `a.c.b` for `fqName` = `a` it returns
     * `a.b` and `a.c`
     *
     * Follow the contract of [com.intellij.psi.PsiElementFinder#getSubPackages]
     */
    fun getSubpackages(fqName: FqName, scope: GlobalSearchScope, nameFilter: (Name) -> Boolean): Collection<FqName> {
        val result = hashSetOf<FqName>()

        FileBasedIndex.getInstance().processValues(
            KotlinPartialPackageNamesIndex.NAME, fqName, null,
            FileBasedIndex.ValueProcessor { _, subPackageName ->
                if (subPackageName != null && nameFilter(subPackageName)) {
                    result.add(fqName.child(subPackageName))
                }
                true
            }, scope
        )

        return result
    }
}