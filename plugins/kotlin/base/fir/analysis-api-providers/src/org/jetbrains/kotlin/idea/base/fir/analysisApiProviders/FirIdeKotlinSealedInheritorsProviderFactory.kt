// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.analysisApiProviders

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.providers.KotlinSealedInheritorsProvider
import org.jetbrains.kotlin.analysis.providers.KotlinSealedInheritorsProviderFactory
import org.jetbrains.kotlin.idea.base.projectStructure.getDependentDependsOnKtModules
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.psi.isExpectDeclaration
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import java.util.concurrent.ConcurrentHashMap

internal class FirIdeKotlinSealedInheritorsProviderFactory(val project: Project) : KotlinSealedInheritorsProviderFactory {
    override fun createSealedInheritorsProvider(): KotlinSealedInheritorsProvider = FirIdeKotlinSealedInheritorsProvider()
}

private class FirIdeKotlinSealedInheritorsProvider : KotlinSealedInheritorsProvider {
    val cache = ConcurrentHashMap<ClassId, List<ClassId>>()

    override fun getSealedInheritors(ktClass: KtClass): List<ClassId> {
        require(ktClass.isSealed())
        val classId = ktClass.classIdIfNonLocal ?: return emptyList() // Local classes cannot be sealed.
        return cache.computeIfAbsent(classId) { getInheritors(classId, ktClass) }
    }

    /**
     * Some notes about the search:
     *  - A Java class cannot legally extend a sealed Kotlin class (even in the same package),
     *    so we don't need to search for Java class inheritors.
     *  - Technically, we could use a package scope to narrow the search, but the search is already sufficiently narrow because it
     *    uses `KotlinSuperClassIndex` and is confined to the current `KtModule` in most cases (except for 'expect' classes).
     *    Finding a `PsiPackage` for a `PackageScope` is not cheap, hence the decision to avoid it.
     *    If a `PackageScope` is needed in the future, it'd be best to extract a `PackageNameScope`
     *    which operates just with the qualified package name, to avoid `PsiPackage`.
     *    (At the time of writing, this is possible with the implementation of `PackageScope`.)
     *  - We ignore local classes to avoid lazy resolve contract violations.
     *    See KT-63795.
     *  - For 'expect' declarations, the search scope includes all modules with a dependsOn dependency on the containing module.
     *    At the same time, 'actual' declarations are restricted to the same module and require no special handling.
     *    See KT-45842.
     *  - KMP libraries are not yet supported.
     *    See KT-65591.
     */
    private fun getInheritors(classId: ClassId, ktClass: KtClass): List<ClassId> {
        val ktModule = ProjectStructureProvider.getModule(ktClass.project, ktClass, contextualModule = null)
        val scope = if (ktClass.isExpectDeclaration()) {
            val dependentDependsOnModules = ktModule.getDependentDependsOnKtModules()
            GlobalSearchScope.union(dependentDependsOnModules.map { it.contentScope } + ktModule.contentScope)
        } else {
            ktModule.contentScope
        }

        return searchInScope(ktClass, classId, scope)
    }

    private fun searchInScope(ktClass: KtClass, classId: ClassId, scope: GlobalSearchScope): List<ClassId> {
        val searchParameters = DirectKotlinClassInheritorsSearch.SearchParameters(
            ktClass = ktClass,
            searchScope = scope,
            includeLocal = false,
        )

        val classIds = DirectKotlinClassInheritorsSearch.search(searchParameters)
            .mapNotNull { (it as? KtClassOrObject)?.classIdIfNonLocal }
            .filter { it.packageFqName == classId.packageFqName }
            .toMutableList()

        // Enforce a deterministic order on the result.
        classIds.sortBy { it.toString() }

        return classIds
    }
}
