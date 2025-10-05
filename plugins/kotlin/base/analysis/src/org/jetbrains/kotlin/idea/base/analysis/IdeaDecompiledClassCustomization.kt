// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.compiled.ClsFileImpl
import org.jetbrains.kotlin.analysis.decompiled.light.classes.DecompiledClassCustomization
import org.jetbrains.kotlin.psi.KtFile

internal class IdeaDecompiledClassCustomization : DecompiledClassCustomization {
    override fun customizeFakeClsFile(
        fakeFile: ClsFileImpl,
        originalKtFile: KtFile
    ) {
        fakeFile.installCodeInsightContextFrom(originalKtFile)
    }

    /**
     * we need to install code insight context from the original [context] file so that [this] file has the same context.
     * todo ijpl-339 ensure all light files are covered
     */
    private fun PsiFile.installCodeInsightContextFrom(
        contextFile: KtFile
    ) {
        if (!isSharedSourceSupportEnabled(project)) {
            return
        }

        val viewProvider = this.viewProvider
        val codeInsightContextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
        codeInsightContextManager.setCodeInsightContext(viewProvider, contextFile.codeInsightContext)
    }
}
