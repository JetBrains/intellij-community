// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.TestOnly

/**
 * Single thread model only (as any other [TextEditorHighlightingPass])
 */
abstract class AbstractHighlightingPassBase(
    protected val file: PsiFile,
    document: Document
) : TextEditorHighlightingPass(file.project, document), DumbAware {

    override fun doCollectInformation(progress: ProgressIndicator) {
        val holder = HighlightInfoHolder(file)
        if (IGNORE_IN_TESTS) {
            assert(ApplicationManager.getApplication().isUnitTestMode)
        }
        else {
            runAnnotatorWithContext(file, holder)
        }
        applyInformationInBackground(holder)
    }

    protected open fun runAnnotatorWithContext(element: PsiElement, holder: HighlightInfoHolder) {
    }

    companion object {
        @Volatile
        private var IGNORE_IN_TESTS: Boolean = false

        /**
         * Make {@link AbstractHighlightingPassBase}-derived passes report nothing inside this method
         */
        @TestOnly
        fun <T> ignoreThesePassesInTests(action: () -> T): T {
            assert(ApplicationManager.getApplication().isUnitTestMode)
            IGNORE_IN_TESTS = true
            try {
              return action.invoke()
            }
            finally {
              IGNORE_IN_TESTS = false
            }
        }
    }

    override fun doApplyInformationToEditor() {
    }

    private fun applyInformationInBackground(holder: HighlightInfoHolder) {
        val result:MutableList<HighlightInfo> = ArrayList(holder.size())
        for (i in 0 until holder.size()) {
            result.add(holder.get(i))
        }
        BackgroundUpdateHighlightersUtil.setHighlightersToEditor(myProject, file, myDocument, 0, file.textLength, result, id)
    }

}