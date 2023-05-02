// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.psi.KtFile

/**
 * Single thread model only (as any other [TextEditorHighlightingPass])
 */
abstract class AbstractHighlightingPassBase(
    protected val file: KtFile,
    document: Document
) : TextEditorHighlightingPass(file.project, document), DumbAware {
    private var annotationHolder: AnnotationHolderImpl? = null

    override fun doCollectInformation(progress: ProgressIndicator) {
        // TODO: YES, IT USES `@ApiStatus.Internal` AnnotationHolderImpl intentionally:
        //  there is no other way to highlight:
        //  - HighlightInfo could not be highlighted immediately as myHighlightInfoProcessor.infoIsAvailable is not accessible
        //  (HighlightingSessionImpl impl is closed) and/or UpdateHighlightersUtil.addHighlighterToEditorIncrementally is closed as well.
        //  therefore direct usage of AnnotationHolderImpl is the smallest evil

        val annotationHolder = AnnotationHolderImpl(AnnotationSession(file), false)
        annotationHolder.runAnnotatorWithContext(file) { element, holder ->
            runAnnotatorWithContext(element, holder)
        }
        this.annotationHolder = annotationHolder
    }

    protected open fun runAnnotatorWithContext(element: PsiElement, holder: AnnotationHolder) {
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
        if (IGNORE_IN_TESTS) {
            assert(ApplicationManager.getApplication().isUnitTestMode)
            return
        }
        try {
            val infos = annotationHolder?.map { HighlightInfo.fromAnnotation(it) } ?: return
            UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, file.textLength, infos, colorsScheme, id)
        } finally {
            annotationHolder = null
        }
    }

}