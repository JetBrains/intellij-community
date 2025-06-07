// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiModuleTest
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractHighLevelQuickFixMultiModuleTest : AbstractQuickFixMultiModuleTest() {

    override fun findAfterFile(editedFile: KtFile): PsiFile? {
        val firAfter = editedFile.containingDirectory?.findFile(editedFile.name + ".fir.after")
        if (firAfter != null) return firAfter
        return editedFile.containingDirectory?.findFile(editedFile.name + ".after")
    }

    override val actionPrefix: String = "K2_ACTION:"
}

@DaemonAnalyzerTestCase.CanChangeDocumentDuringHighlighting
abstract class AbstractHighLevelWithPostponedQuickFixMultiModuleTest: AbstractHighLevelQuickFixMultiModuleTest()