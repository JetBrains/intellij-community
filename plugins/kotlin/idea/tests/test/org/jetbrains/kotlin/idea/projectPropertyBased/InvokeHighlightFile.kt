// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectPropertyBased

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.propertyBased.ActionOnFile
import com.intellij.testFramework.propertyBased.RehighlightAllEditors
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.kotlin.idea.util.isUnderKotlinSourceRootTypes

class InvokeHighlightFile(file: PsiFile): ActionOnFile(file) {
    override fun performCommand(env: ImperativeCommand.Environment) {
        val project = project
        val editor =
            FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, virtualFile, 0), true)
                ?: error("Unable to open file $virtualFile")

        env.logMessage("Highlight ${file.virtualFile}")
        val highlightInfos = RehighlightAllEditors.highlightEditor(editor, project)
        val errorHighlightInfos = highlightInfos.filter { it.type == HighlightInfoType.ERROR }
        env.logMessage("${highlightInfos.size} highlighted infos, ${errorHighlightInfos.size} of them are errors")

        check(!(errorHighlightInfos.isNotEmpty() && file.isUnderKotlinSourceRootTypes())) {
            "$virtualFile has error elements: ${errorHighlightInfos}"
        }
    }
}