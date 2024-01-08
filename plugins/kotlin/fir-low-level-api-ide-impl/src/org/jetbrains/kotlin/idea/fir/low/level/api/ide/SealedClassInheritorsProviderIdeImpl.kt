// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.low.level.api.ide

import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.isSealed
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.searching.inheritors.DirectKotlinClassInheritorsSearch
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import java.util.concurrent.ConcurrentHashMap

internal class SealedClassInheritorsProviderIdeImpl : SealedClassInheritorsProvider() {
    val cache = ConcurrentHashMap<ClassId, List<ClassId>>()

    @OptIn(SealedClassInheritorsProviderInternals::class)
    override fun getSealedClassInheritors(firClass: FirRegularClass): List<ClassId> {
        require(firClass.isSealed)
        firClass.sealedInheritorsAttr?.value?.let { return it }
        return cache.computeIfAbsent(firClass.classId) { getInheritors(firClass) }
    }

    private fun getInheritors(firClass: FirRegularClass): List<ClassId> {
        val sealedKtClass = firClass.psi as? KtClass ?: return emptyList()
        val classId = sealedKtClass.getClassId() ?: return emptyList()
        val ktModule = ProjectStructureProvider.getModule(sealedKtClass.project, sealedKtClass, contextualModule = null)

        // Some notes about the search:
        //  - A Java class cannot legally extend a sealed Kotlin class (even in the same package), so we don't need to search for Java class
        //    inheritors.
        //  - Technically, we could use a package scope here to narrow the search, but the search is already sufficiently narrow because it
        //    uses `KotlinSuperClassIndex` and is confined to the current `KtModule`. Finding a `PsiPackage` for a `PackageScope` is not
        //    cheap, hence the decision to avoid it. If a `PackageScope` is needed in the future, it'd be best to extract a
        //    `PackageNameScope` which operates just with the qualified package name, to avoid `PsiPackage`. (At the time of writing, this
        //    is possible with the implementation of `PackageScope`.)
        //  - KMP is unlikely to be fully supported. See KTIJ-28421.
        val classIds = DirectKotlinClassInheritorsSearch.search(sealedKtClass, ktModule.contentScope)
            .mapNotNull { (it as? KtClassOrObject)?.classIdIfNonLocal }
            .filter { it.packageFqName == classId.packageFqName }
            .toMutableList()

        // Enforce a deterministic order on the result.
        classIds.sortBy { it.toString() }

        return classIds
    }
}
