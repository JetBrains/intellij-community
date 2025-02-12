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
import org.jetbrains.kotlin.resolve.jvm.TopPackageNamesProvider
import org.jetbrains.kotlin.utils.ifEmpty

object KotlinPackageIndexUtils {
    private val falseValueProcessor = FileBasedIndex.ValueProcessor<Name?> { _, _ -> false }

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
    ): Boolean {
        if (certainlyDoesNotExist(packageFqName, searchScope)) return false
        return !FileBasedIndex.getInstance().processValues(
            KotlinPartialPackageNamesIndex.NAME,
            packageFqName,
            null,
            falseValueProcessor,
            searchScope
        )
    }

    private fun certainlyDoesNotExist(
        packageFqName: FqName,
        searchScope: GlobalSearchScope
    ): Boolean {
        val provider = searchScope as? TopPackageNamesProvider ?: return false
        val topPackageNames = provider.topPackageNames ?: return false
        val packageFqNameTopLevelPackage = packageFqName.asString().substringBefore(".")
        return packageFqNameTopLevelPackage !in topPackageNames
    }

    /**
     * Return all direct subpackages of package [fqName].
     *
     * For example, if there are packages `a.b`, `a.b.c`, `a.c`, `a.c.b`, for `fqName` = `a` it returns `a.b` and `a.c`.
     *
     * Follows the contract of [com.intellij.psi.PsiElementFinder.getSubPackages].
     */
    fun getSubpackages(fqName: FqName, scope: GlobalSearchScope, nameFilter: (Name) -> Boolean): Set<FqName> {
        if (certainlyDoesNotExist(fqName, scope)) return emptySet()

        val result = hashSetOf<FqName>()
        forEachSubpackageName(fqName, scope, nameFilter) { name ->
            result.add(fqName.child(name))
        }
        return result.ifEmpty { emptySet() }
    }

    /**
     * Return all direct subpackage names of package [fqName].
     *
     * For example, if there are packages `a.b`, `a.b.c`, `a.c`, `a.c.b`, for `fqName` = `a` it returns `b` and `c`.
     *
     * Follows the contract of [com.intellij.psi.PsiElementFinder.getSubPackages].
     */
    fun getSubpackageNames(fqName: FqName, scope: GlobalSearchScope): Set<Name> {
        if (certainlyDoesNotExist(fqName, scope)) return emptySet()

        val result = hashSetOf<Name>()
        forEachSubpackageName(fqName, scope, nameFilter = { true }) { name ->
            result.add(name)
        }
        return result.ifEmpty { emptySet() }
    }

    inline fun forEachSubpackageName(
        fqName: FqName,
        scope: GlobalSearchScope,
        nameFilter: (Name) -> Boolean,
        action: (Name) -> Unit,
    ) {
        // use getValues() instead of processValues() because the latter visits each file in the package and that could be slow if there are a lot of files
        val values = FileBasedIndex.getInstance().getValues(KotlinPartialPackageNamesIndex.NAME, fqName, scope)
        for (subPackageName in values) {
            if (subPackageName != null && nameFilter(subPackageName)) {
                action(subPackageName)
            }
        }
    }
}
