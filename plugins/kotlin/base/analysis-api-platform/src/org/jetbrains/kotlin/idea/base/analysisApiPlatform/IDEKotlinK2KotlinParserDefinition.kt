// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysisApiPlatform

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.analysis.isInjectedFileShouldBeAnalyzed
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.*

internal class IDEKotlinK2KotlinParserDefinition : KotlinParserDefinition() {
    override fun createFile(fileViewProvider: FileViewProvider): PsiFile {
        val injectedLanguageManager = InjectedLanguageManager.getInstance(fileViewProvider.manager.project)
        val isInjection = injectedLanguageManager.isInjectedViewProvider(fileViewProvider)

        return if (isInjection && !fileViewProvider.isInjectedFileShouldBeAnalyzed) {
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