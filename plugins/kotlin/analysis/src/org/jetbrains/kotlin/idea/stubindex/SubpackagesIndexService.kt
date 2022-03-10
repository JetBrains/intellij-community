// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.stubindex

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndex.ValueProcessor
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.idea.vfilefinder.KotlinPartialPackageNamesIndex
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Responsible for Kotlin package only
 */
class SubpackagesIndexService(private val project: Project) {
    /**
     * Return true if exists package with exact [fqName] OR there are some subpackages of [fqName]
     */
    fun packageExists(fqName: FqName): Boolean = packageExists(fqName, GlobalSearchScope.allScope(project))

    /**
     * Return true if package [fqName] exists or some subpackages of [fqName] exist in [scope]
     */
    fun packageExists(fqName: FqName, scope: GlobalSearchScope): Boolean = !FileBasedIndex.getInstance().processValues(
        KotlinPartialPackageNamesIndex.KEY,
        fqName,
        null,
        ValueProcessor { _, _ -> false },
        scope
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

        FileBasedIndex.getInstance().processValues(KotlinPartialPackageNamesIndex.KEY, fqName, null, ValueProcessor { _, subPackageName ->
            if (subPackageName != null && nameFilter(subPackageName)) {
                result.add(fqName.child(subPackageName))
            }
            true
        }, scope)

        return result
    }

    companion object {
        fun getInstance(project: Project): SubpackagesIndexService {
            return project.getServiceSafe()
        }
    }
}