// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.notifications

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.util.containers.TreeTraversal
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens.SHEBANG_COMMENT
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.function.Function
import javax.swing.JComponent

class MainKtsScriptNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!isMainKtsScript(file) || !file.isKotlinFileType()) {
            return null
        }

        return Function { fileEditor ->
          when {
            shouldBeExecutable(file, project) ->
              EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text(KotlinBaseScriptingBundle.message("notification.main.kts.unable.execute"))
                createActionLabel(KotlinBaseScriptingBundle.message("notification.main.kts.make.executable")) {
                  File(file.path).setExecutable(true)
                  EditorNotifications.getInstance(project).updateNotifications(file)
                }
              }

            else -> null
          }
        }
    }

    private fun shouldBeExecutable(file: VirtualFile, project: Project): Boolean {
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return false
        return ktFile.hasShebangComment() && !File(file.path).canExecute()
    }

    companion object {
        private const val MAIN_KTS = "main.kts"
    }

    private fun isMainKtsScript(virtualFile: VirtualFile) = virtualFile.name == MAIN_KTS || virtualFile.name.endsWith(".$MAIN_KTS")

    private fun PsiFile.hasShebangComment(): Boolean =
        SyntaxTraverser.psiTraverser(/* root = */ this)
            .withTraversal(TreeTraversal.LEAVES_DFS)
            .traverse()
            .filterIsInstance<PsiComment>()
            .any { element: PsiElement -> element.isShebangComment() }

    private fun PsiElement.isShebangComment(): Boolean =
        this is PsiComment && tokenType === SHEBANG_COMMENT
}