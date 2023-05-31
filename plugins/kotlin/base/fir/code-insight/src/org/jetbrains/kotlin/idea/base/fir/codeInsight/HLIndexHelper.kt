// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.SmartList
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.analysis.providers.KotlinResolutionScopeProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.isKotlinBuiltins
import org.jetbrains.kotlin.idea.stubindex.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration

/**
 *TODO get rid of this class, replace with [org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider]
 */
class HLIndexHelper(val project: Project, private val scope: GlobalSearchScope) {
    fun getTopLevelCallables(nameFilter: (Name) -> Boolean): Collection<KtCallableDeclaration> {
        val values = SmartList<KtCallableDeclaration>()
        val processor = CancelableCollectFilterProcessor(values) {
            !it.isKotlinBuiltins() && it.receiverTypeReference == null
        }

        val keyFilter: (String) -> Boolean = { nameFilter(getShortName(it)) }
        KotlinTopLevelFunctionFqnNameIndex.processAllElements(project, scope, keyFilter, processor)
        KotlinTopLevelPropertyFqnNameIndex.processAllElements(project, scope, keyFilter, processor)

        return values
    }

    fun getTopLevelExtensions(nameFilter: (Name) -> Boolean, receiverTypeNames: Set<String>): Collection<KtCallableDeclaration> =
        KotlinTopLevelExtensionsByReceiverTypeIndex.getAllElements(
            project,
            scope,
            {
                KotlinTopLevelExtensionsByReceiverTypeIndex.receiverTypeNameFromKey(it) in receiverTypeNames &&
                        nameFilter(Name.identifier(KotlinTopLevelExtensionsByReceiverTypeIndex.callableNameFromKey(it)))
            },
            valueFilter = { !it.isKotlinBuiltins() }
        )

    fun getPossibleTypeAliasExpansionNames(originalTypeName: String): Set<String> {
        val out = mutableSetOf<String>()

        fun searchRecursively(typeName: String) {
            ProgressManager.checkCanceled()
            KotlinTypeAliasByExpansionShortNameIndex[typeName, project, scope]
                .asSequence()
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

        fun createForPosition(position: PsiElement): HLIndexHelper {
            val project = position.project
            val module = ProjectStructureProvider.getModule(project, position, null)
            val scope = KotlinResolutionScopeProvider.getInstance(project).getResolutionScope(module)
            return HLIndexHelper(project, scope)
        }
    }
}
