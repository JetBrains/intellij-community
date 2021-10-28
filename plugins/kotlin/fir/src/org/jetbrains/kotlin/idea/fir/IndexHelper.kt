// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.search.AllClassesSearchExecutor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.kotlin.analysis.project.structure.allDirectDependencies
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.refactoring.fqName.isJavaClassNotToBeUsedInKotlin
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.idea.util.isSyntheticKotlinClass
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedDeclaration

/*
* Move to another module
*/
class HLIndexHelper(val project: Project, private val scope: GlobalSearchScope) {
    fun getClassNamesInPackage(packageFqName: FqName): Set<Name> =
        KotlinTopLevelClassByPackageIndex.getInstance()
            .get(packageFqName.asStringForIndexes(), project, scope)
            .mapNotNullTo(hashSetOf()) { it.nameAsName }

    fun getKotlinClasses(
        nameFilter: (Name) -> Boolean,
        psiFilter: (element: KtClassOrObject) -> Boolean = { true }
    ): Collection<KtClassOrObject> {
        val index = KotlinFullClassNameIndex.getInstance()
        return index.getAllKeys(project).asSequence()
            .onEach { ProgressManager.checkCanceled() }
            .filter { fqName -> nameFilter(getShortName(fqName)) }
            .flatMap { fqName -> index[fqName, project, scope] }
            .filter(psiFilter)
            .toList()
    }

    fun getTopLevelCallables(nameFilter: (Name) -> Boolean): Collection<KtCallableDeclaration> {
        fun sequenceOfElements(index: StringStubIndexExtension<out KtCallableDeclaration>): Sequence<KtCallableDeclaration> =
            index.getAllKeys(project).asSequence()
                .onEach { ProgressManager.checkCanceled() }
                .filter { fqName -> nameFilter(getShortName(fqName)) }
                .flatMap { fqName -> index[fqName, project, scope] }
                .filter { it.receiverTypeReference == null }

        val functions = sequenceOfElements(KotlinTopLevelFunctionFqnNameIndex.getInstance())
        val properties = sequenceOfElements(KotlinTopLevelPropertyFqnNameIndex.getInstance())

        return (functions + properties).toList()
    }

    fun getKotlinCallablesByName(name: Name): Collection<KtCallableDeclaration> {
        val functions: Sequence<KtCallableDeclaration> =
            KotlinFunctionShortNameIndex.getInstance().get(name.asString(), project, scope).asSequence()

        val properties: Sequence<KtNamedDeclaration> =
            KotlinPropertyShortNameIndex.getInstance().get(name.asString(), project, scope).asSequence()

        return (functions + properties)
            .filterIsInstance<KtCallableDeclaration>()
            .toList()
    }

    fun getKotlinClassesByName(name: Name): Collection<KtClassOrObject> {
        return KotlinClassShortNameIndex.getInstance().get(name.asString(), project, scope)
    }

    fun getTopLevelExtensions(nameFilter: (Name) -> Boolean, receiverTypeNames: Set<String>): Collection<KtCallableDeclaration> {
        val index = KotlinTopLevelExtensionsByReceiverTypeIndex.INSTANCE

        return index.getAllKeys(project).asSequence()
            .onEach { ProgressManager.checkCanceled() }
            .filter { KotlinTopLevelExtensionsByReceiverTypeIndex.receiverTypeNameFromKey(it) in receiverTypeNames }
            .filter { nameFilter(Name.identifier(KotlinTopLevelExtensionsByReceiverTypeIndex.callableNameFromKey(it))) }
            .flatMap { key -> index[key, project, scope] }
            .toList()
    }

    fun getPossibleTypeAliasExpansionNames(originalTypeName: String): Set<String> {
        val index = KotlinTypeAliasByExpansionShortNameIndex.INSTANCE
        val out = mutableSetOf<String>()

        fun searchRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            index[typeName, project, scope].asSequence()
                .mapNotNull { it.name }
                .filter { out.add(it) }
                .forEach(::searchRecursively)
        }

        searchRecursively(originalTypeName)
        return out
    }

    fun getJavaClasses(nameFilter: (Name) -> Boolean): Collection<PsiClass> {
        val names = mutableSetOf<String>()
        AllClassesSearchExecutor.processClassNames(project, scope) { name ->
            if (nameFilter(Name.identifier(name))) {
                names.add(name)
            }
            true
        }
        val result = mutableListOf<PsiClass>()
        AllClassesSearchExecutor.processClassesByNames(project, scope, names) { psiClass ->
            // Skip Kotlin classes
            if (psiClass is KtLightClass ||
                psiClass.isSyntheticKotlinClass() ||
                psiClass.getKotlinFqName()?.isJavaClassNotToBeUsedInKotlin() == true
            )
                return@processClassesByNames true

            result.add(psiClass)
            true
        }
        return result
    }

    companion object {
        private fun CallableId.asStringForIndexes(): String =
            (if (packageName.isRoot) callableName.asString() else toString()).replace('/', '.')

        private fun FqName.asStringForIndexes(): String =
            asString().replace('/', '.')

        private fun ClassId.asStringForIndexes(): String =
            asSingleFqName().asStringForIndexes()

        private fun getShortName(fqName: String) = Name.identifier(fqName.substringAfterLast('.'))

        @OptIn(ExperimentalStdlibApi::class)
        fun createForPosition(position: PsiElement): HLIndexHelper {
            val module = position.getKtModule()
            val allScopes = module.allDirectDependencies().mapTo(mutableSetOf()) { it.contentScope }
            allScopes.add(module.contentScope)
            return HLIndexHelper(position.project, GlobalSearchScope.union(allScopes))
        }
    }
}
