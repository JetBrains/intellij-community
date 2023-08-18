// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.intentions.tests

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.testIntegration.GotoTestOrCodeHandler
import org.jetbrains.kotlin.idea.actions.AbstractNavigationTest

abstract class AbstractK2GotoTestOrCodeActionTest : AbstractNavigationTest() {
    override fun isFirPlugin(): Boolean = true

    private object Handler : GotoTestOrCodeHandler() {
        public override fun getSourceAndTargetElements(editor: Editor?, file: PsiFile?) = super.getSourceAndTargetElements(editor, file)
    }

    override fun getSourceAndTargetElements(editor: Editor, file: PsiFile) = Handler.getSourceAndTargetElements(editor, file)
}
