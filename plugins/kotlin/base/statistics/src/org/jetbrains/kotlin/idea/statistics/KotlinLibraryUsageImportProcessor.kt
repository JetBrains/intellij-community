// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.libraryUsage.LibraryUsageImportProcessor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinLibraryUsageImportProcessor : LibraryUsageImportProcessor<KtImportDirective> {
    @OptIn(UnsafeCastFunction::class)
    override fun imports(file: PsiFile): List<KtImportDirective> = file.safeAs<KtFile>()?.importDirectives.orEmpty()

    override fun isSingleElementImport(import: KtImportDirective): Boolean = !import.isAllUnder

    override fun importQualifier(import: KtImportDirective): String? = import.importedFqName?.asString()

    override fun resolve(import: KtImportDirective): PsiElement? = import.importedReference
        ?.getQualifiedElementSelector()
        ?.mainReference
        ?.resolve()
}
