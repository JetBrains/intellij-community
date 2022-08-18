// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.kotlin.analysis.project.structure.allDirectDependencies
import org.jetbrains.kotlin.analysis.project.structure.getKtModule
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration

class HLIndexHelper(val project: Project, private val scope: GlobalSearchScope) {
    fun getTopLevelCallables(nameFilter: (Name) -> Boolean): Collection<KtCallableDeclaration> {
        fun sequenceOfElements(index: StringStubIndexExtension<out KtCallableDeclaration>): Sequence<KtCallableDeclaration> =
            index.getAllKeys(project).asSequence()
                .onEach { ProgressManager.checkCanceled() }
                .filter { fqName -> nameFilter(getShortName(fqName)) }
                .flatMap { fqName -> index[fqName, project, scope] }
                .filter { it.receiverTypeReference == null }

        val functions = sequenceOfElements(KotlinTopLevelFunctionFqnNameIndex)
        val properties = sequenceOfElements(KotlinTopLevelPropertyFqnNameIndex)

        return (functions + properties).toList()
    }

    fun getTopLevelExtensions(nameFilter: (Name) -> Boolean, receiverTypeNames: Set<String>): Collection<KtCallableDeclaration> {
        val index = KotlinTopLevelExtensionsByReceiverTypeIndex

        return index.getAllKeys(project).asSequence()
            .onEach { ProgressManager.checkCanceled() }
            .filter { KotlinTopLevelExtensionsByReceiverTypeIndex.receiverTypeNameFromKey(it) in receiverTypeNames }
            .filter { nameFilter(Name.identifier(KotlinTopLevelExtensionsByReceiverTypeIndex.callableNameFromKey(it))) }
            .flatMap { key -> index[key, project, scope] }
            .toList()
    }

    fun getPossibleTypeAliasExpansionNames(originalTypeName: String): Set<String> {
        val index = KotlinTypeAliasByExpansionShortNameIndex
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

    companion object {
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
