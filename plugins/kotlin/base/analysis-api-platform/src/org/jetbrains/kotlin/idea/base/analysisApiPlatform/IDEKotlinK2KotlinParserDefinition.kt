// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedFileViewProvider
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class IDEKotlinK2KotlinParserDefinition : KotlinParserDefinition() {
    override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
        return if (fileViewProvider is InjectedFileViewProvider) {
            KtBlockCodeFragment(
                fileViewProvider,
                imports = null,
                context = createContextElementForInjection(fileViewProvider),/*hack for until KT-70796 is fixed*/
            )
        } else {
            KtFile(fileViewProvider, false)
        }
    }

    private fun createContextElementForInjection(fileViewProvider: FileViewProvider): KtExpression {
        val ktFile = KtPsiFactory(fileViewProvider.manager.project).createFile("fun injectedFragment() { Unit }")
        val function = ktFile.declarations.single() as KtNamedFunction
        return function.bodyBlockExpression!!.statements.single()
    }
}