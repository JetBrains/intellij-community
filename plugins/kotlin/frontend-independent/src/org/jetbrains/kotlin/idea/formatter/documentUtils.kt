// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.DocumentUtil

fun Document.adjustLineIndent(project: Project, offset: Int) {
    CodeStyleManager.getInstance(project).adjustLineIndent(this, DocumentUtil.getLineStartOffset(offset, this))
}