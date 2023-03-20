// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.refactoring.cutPaste

import com.intellij.codeHighlighting.*
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.refactoring.suggested.range

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

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass {
        return MyPass(file.project, file, editor)
    }

    private class MyPass(
        private val project: Project,
        private val file: PsiFile,
        private val editor: Editor
    ) : TextEditorHighlightingPass(project, editor.document, true) {

        @Volatile
        private var myInfo: HighlightInfo? = null

        override fun doCollectInformation(progress: ProgressIndicator) {
            myInfo = buildHighlightingInfo()
        }

        override fun doApplyInformationToEditor() {
            val info = myInfo
            if (info != null) {
                UpdateHighlightersUtil.setHighlightersToEditor(project, myDocument, 0, file.textLength, listOf(info), colorsScheme, id)
            }
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
                .range(cookie.bounds.range!!)
                .registerFix(MoveDeclarationsIntentionAction(processor, cookie.bounds, cookie.modificationCount), null, null, null, null)
                .createUnconditionally()
        }
    }
}
