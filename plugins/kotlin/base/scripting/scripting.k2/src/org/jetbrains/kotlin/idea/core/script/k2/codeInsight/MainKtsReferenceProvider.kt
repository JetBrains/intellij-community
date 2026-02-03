// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.codeInsight

import com.intellij.model.Pointer
import com.intellij.model.SingleTargetReference
import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.navigation.NavigatableSymbol
import com.intellij.navigation.SymbolNavigationService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.resolveFromRootOrRelative
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.Unmodifiable
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile

private val IMPORT_FQN: FqName = FqName("org.jetbrains.kotlin.mainKts.Import")

class MainKtsReferenceProvider : ImplicitReferenceProvider {
    @OptIn(KaAllowAnalysisOnEdt::class)
    fun isImportAnnotation(element: KtAnnotationEntry): Boolean {
        return allowAnalysisOnEdt {
            analyze(element) {
                val symbol = element.calleeExpression?.resolveToCall()?.singleFunctionCallOrNull()?.symbol as? KaConstructorSymbol
                symbol?.containingClassId?.asSingleFqName()
            }
        } == IMPORT_FQN
    }

    override fun getImplicitReference(
        element: PsiElement,
        offsetInElement: Int
    ): PsiSymbolReference? {
        if ((element.containingFile as? KtFile)?.isScript() != true) return null
        val annotation = element.parentOfType<KtAnnotationEntry>() ?: return null
        if (!isImportAnnotation(annotation)) return null

        val length = element.textRange.length
        return ImportedScriptReference(element, TextRange(0, length), element.text)
    }
}

internal class ImportedScriptReference(
    private val element: PsiElement,
    private val range: TextRange,
    private val relativeFileLocation: String
) : SingleTargetReference(), PsiSymbolReference {

    override fun resolveSingleTarget(): Symbol? {
        val relativeFile = element.containingFile.virtualFile?.parent?.resolveFromRootOrRelative(relativeFileLocation)
        if (relativeFile != null) {
            return ImportedScriptSymbol(relativeFile.path)
        }

        val searchScope = GlobalSearchScope.projectScope(element.project)
        val absoluteFile = FilenameIndex.getVirtualFilesByName(relativeFileLocation, searchScope).singleOrNull() ?: return null
        return ImportedScriptSymbol(absoluteFile.path)
    }

    override fun getElement(): PsiElement = element

    override fun getRangeInElement(): TextRange = range
}

internal class ImportedScriptSymbol(val filePath: String) : NavigatableSymbol {
    override fun getNavigationTargets(project: Project): @Unmodifiable Collection<NavigationTarget?> {
        val psiFile = findScriptFile(project) ?: return emptyList()
        return listOf(SymbolNavigationService.getInstance().psiFileNavigationTarget(psiFile))
    }

    private fun findScriptFile(project: Project): PsiFile? {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return null
        return PsiManager.getInstance(project).findFile(virtualFile)
    }

    override fun createPointer(): Pointer<ImportedScriptSymbol> = Pointer.hardPointer(this)
}
