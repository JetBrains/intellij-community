// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.hierarchy

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches

fun getCurrentElement(dataContext: DataContext, project: Project): PsiElement? {
    val editor = CommonDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        if (!RootKindFilter.projectAndLibrarySources.matches(file)) return null
        return TargetElementUtil.findTargetElement(editor, TargetElementUtil.getInstance().allAccepted)
    }

    return CommonDataKeys.PSI_ELEMENT.getData(dataContext)
}
