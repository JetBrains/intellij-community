// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveVisitor
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.references.KtReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*

internal class UsedReferencesCollector(private val file: KtFile) {

    data class Result(
        val usedDeclarations: Map<FqName, Set<Name>>,
        val unresolvedNames: Set<Name>,
        val usedSymbols: Set<SymbolInfo>,
        val references: Collection<KtReference>,
    )

    private val unresolvedNames: HashSet<Name> = hashSetOf()
    private val usedDeclarations: HashMap<FqName, MutableSet<Name>> = hashMapOf()
    private val importableSymbols: HashSet<SymbolInfo> = hashSetOf()
    private val references: HashSet<KtReference> = hashSetOf()

    private val aliases: Map<FqName, List<Name>> = collectImportAliases(file)

    fun KaSession.collectUsedReferences(): Result {
        file.accept(object : KtVisitorVoid(),PsiRecursiveVisitor {
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

        val importableSymbolPointers = importableSymbols.toSet()

        return Result(usedDeclarations, unresolvedNames, importableSymbolPointers, references)
    }

    private fun KaSession.collectReferencesFrom(element: KtElement) {
        if (element.ignoreReferencesDuringImportOptimization) return

        if (element is KtLabelReferenceExpression) return

        val usedReferences = element.references
            .filterIsInstance<KtReference>()
            .mapNotNull { UsedReference.run { createFrom(it) } }

        if (usedReferences.isEmpty()) return

        for (usedReference in usedReferences) {
            ProgressIndicatorProvider.checkCanceled()

            references += usedReference.reference

            val isResolved = usedReference.run { isResolved() }

            val names = usedReference.run { resolvesByNames() }
            if (!isResolved) {
                unresolvedNames += names
                continue
            }

            val symbols = usedReference.run { resolveToReferencedSymbols() }

            for (symbol in symbols) {
                if (!symbol.run { isResolvedWithImport() }) continue

                val importableName = symbol.run { computeImportableName() } ?: continue

                // Do not save symbols from the current package unless they are aliased
                if (importableName.parent() == file.packageFqName && importableName !in aliases) continue

                ProgressIndicatorProvider.checkCanceled()

                val newNames = (aliases[importableName].orEmpty() + importableName.shortName()).intersect(names)
                usedDeclarations.getOrPut(importableName) { hashSetOf() } += newNames

                importableSymbols += symbol.run { toSymbolInfo() }
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
