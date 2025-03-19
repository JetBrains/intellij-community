// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.libraries

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.isProjectSyncPendingOrInProgress

internal class AddKotlinLibraryQuickFix(
    private val dependencyManager: KotlinBuildSystemDependencyManager,
    private val libraryDescriptor: ExternalLibraryDescriptor,
    @IntentionName
    private val quickFixText: String
) : IntentionAction, HighPriorityAction {
    override fun getText(): String = quickFixText
    override fun getFamilyName(): String = quickFixText
    override fun startInWriteAction(): Boolean = false
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        val module = file?.module ?: return false
        return dependencyManager.isApplicable(module) && !dependencyManager.isProjectSyncPendingOrInProgress()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null && !ApplicationManager.getApplication().isHeadlessEnvironment) return
        if (file == null) return
        val psiFile = file.originalFile
        val module = psiFile.module ?: return

        ApplicationManager.getApplication().runWriteAction {
            dependencyManager.addDependency(module, libraryDescriptor)
        }

        dependencyManager.startProjectSync()

        val buildScriptFile = dependencyManager.getBuildScriptFile(module)
        if (buildScriptFile != null) {
            FileEditorManager.getInstance(module.project).openFile(buildScriptFile, false)
        }
    }
}