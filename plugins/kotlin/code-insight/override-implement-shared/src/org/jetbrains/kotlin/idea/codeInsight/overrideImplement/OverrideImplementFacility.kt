// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.overrideImplement

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

interface OverrideImplementFacility {
    fun implement(editor: Editor, file: PsiFile, implementAll: Boolean)
    fun override(editor: Editor, file: PsiFile, implementAll: Boolean)

    companion object {
        fun getInstance(): OverrideImplementFacility {
            return service()
        }
    }
}