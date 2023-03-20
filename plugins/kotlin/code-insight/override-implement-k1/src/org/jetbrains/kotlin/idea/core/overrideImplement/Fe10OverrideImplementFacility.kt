// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeInsight.overrideImplement.OverrideImplementFacility

class Fe10OverrideImplementFacility : OverrideImplementFacility {
    override fun override(editor: Editor, file: PsiFile, implementAll: Boolean) {
        OverrideMembersHandler().invoke(file.project, editor, file, implementAll)
    }

    override fun implement(editor: Editor, file: PsiFile, implementAll: Boolean) {
        ImplementMembersHandler().invoke(file.project, editor, file, implementAll)
    }
}