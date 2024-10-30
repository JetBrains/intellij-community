// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.cutPaste

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker

class MoveDeclarationsPassFactory : TextEditorHighlightingPassFactory {

    class Registrar : TextEditorHighlightingPassFactoryRegistrar {
        override fun registerHighlightingPassFactory(registrar: TextEditorHighlightingPassRegistrar, project: Project) {
            registrar.registerTextEditorHighlightingPass(
                MoveDeclarationsPassFactory(),
                TextEditorHighlightingPassRegistrar.Anchor.BEFORE,
                Pass.POPUP_HINTS,
                true,
                true
            )
        }
    }

    override fun createHighlightingPass(psiFile: PsiFile, editor: Editor): TextEditorHighlightingPass {
        return MoveDeclarationsPass(psiFile.project, psiFile, editor)
    }

    private class MoveDeclarationsPass(
        private val project: Project,
        private val file: PsiFile,
        private val editor: Editor
    ) : TextEditorHighlightingPass(project, editor.document, true) {

        override fun doCollectInformation(progress: ProgressIndicator) {
            val info = buildHighlightingInfo()
            if (info != null) {
                BackgroundUpdateHighlightersUtil.setHighlightersToEditor(project, file, myDocument, 0, file.textLength, listOf(info), id)
            }
        }

        override fun doApplyInformationToEditor() {
        }

        private fun buildHighlightingInfo(): HighlightInfo? {
            val cookie = editor.getUserData(MoveDeclarationsEditorCookie.KEY) ?: return null

            if (cookie.modificationCount != PsiModificationTracker.getInstance(project).modificationCount) return null

            val processor = MoveDeclarationsProcessor.build(file, cookie)

            if (processor == null) {
                editor.putUserData(MoveDeclarationsEditorCookie.KEY, null)
                return null
            }

            return HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
                .range(cookie.bounds.asTextRange!!)
                .registerFix(MoveDeclarationsIntentionAction(processor, cookie.bounds, cookie.modificationCount), null, null, null, null)
                .createUnconditionally()
        }
    }
}
