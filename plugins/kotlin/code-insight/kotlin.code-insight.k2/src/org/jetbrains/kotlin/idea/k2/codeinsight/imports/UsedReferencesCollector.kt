// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.ContributedReferenceHost
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import kotlin.collections.orEmpty
import kotlin.collections.plusAssign

internal class UsedReferencesCollector(private val file: KtFile) {

    data class Result(
        val usedDeclarations: Map<FqName, Set<Name>>,
        val unresolvedNames: Set<Name>,
    )

    private val unresolvedNames: HashSet<Name> = hashSetOf()
    private val usedDeclarations: HashMap<FqName, MutableSet<Name>> = hashMapOf()

    private val aliases: Map<FqName, List<Name>> = collectImportAliases(file)

    fun KaSession.collectUsedReferences(): Result {
        file.accept(object : KtVisitorVoid() {
            override fun visitElement(element: PsiElement) {
                ProgressIndicatorProvider.checkCanceled()
                element.acceptChildren(this)
            }

            override fun visitImportList(importList: KtImportList) {}

            override fun visitPackageDirective(directive: KtPackageDirective) {}

            override fun visitKtElement(element: KtElement) {
                super.visitKtElement(element)

                collectReferencesFrom(element)
            }
        })

        return Result(usedDeclarations, unresolvedNames)
    }

    private fun KaSession.collectReferencesFrom(element: KtElement) {
        // we ignore such elements because resolving them leads to UAST resolution,
        // and that in turn leads to KT-68601 when import optimization is called after move refactoring
        if (element is ContributedReferenceHost) return

        if (element is KtLabelReferenceExpression) return

        val references = element.references
            .filterIsInstance<KtReference>()
            .mapNotNull { UsedReference.run { createFrom(it) } }

        if (references.isEmpty()) return

        for (reference in references) {
            ProgressIndicatorProvider.checkCanceled()

            val isResolved = reference.run { isResolved() }

            val names = reference.resolvesByNames
            if (!isResolved) {
                unresolvedNames += names
                continue
            }

            val symbols = reference.run { resolveToImportableSymbols() }

            for (symbol in symbols) {
                if (!symbol.run { isResolvedWithImport() }) continue

                val importableName = symbol.run { computeImportableFqName() } ?: continue

                ProgressIndicatorProvider.checkCanceled()

                val newNames = (aliases[importableName].orEmpty() + importableName.shortName()).intersect(names)
                usedDeclarations.getOrPut(importableName) { hashSetOf() } += newNames
            }
        }
    }
}

private fun collectImportAliases(file: KtFile): Map<FqName, List<Name>> = if (file.hasImportAlias()) {
    file.importDirectives
        .asSequence()
        .filter { !it.isAllUnder && it.alias != null }
        .mapNotNull { it.importPath }
        .groupBy(keySelector = { it.fqName }, valueTransform = { it.importedName as Name })
} else {
    emptyMap()
}
