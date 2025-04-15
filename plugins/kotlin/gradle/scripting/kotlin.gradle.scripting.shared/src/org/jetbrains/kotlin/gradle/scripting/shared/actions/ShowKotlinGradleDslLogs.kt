// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.shared.actions

import KotlinGradleScriptingBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiFile
import com.intellij.ui.BrowserHyperlinkListener
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

class ShowKotlinGradleDslLogs : IntentionAction, AnAction(), DumbAware {
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        openLogsDirIfPresent(project)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        openLogsDirIfPresent(project)
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = RevealFileAction.isSupported()

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        presentation.isEnabledAndVisible = e.project != null && RevealFileAction.isSupported()
        presentation.text = NAME
    }

    private fun openLogsDirIfPresent(project: Project) {
        val logsDir = findLogsDir()
        if (logsDir != null) {
            RevealFileAction.openDirectory(logsDir)
        } else {
            val parent = WindowManager.getInstance().getStatusBar(project)?.component
                         ?: WindowManager.getInstance().findVisibleFrame()?.rootPane

            JBPopupFactory.getInstance()
                .createHtmlTextBalloonBuilder(
                  KotlinGradleScriptingBundle.message(
                        "text.gradle.dsl.logs.cannot.be.found.automatically.see.how.to.find.logs",
                        gradleTroubleshootingLink
                    ),
                  MessageType.ERROR,
                  BrowserHyperlinkListener.INSTANCE
                )
                .setFadeoutTime(5000)
                .createBalloon()
                .showInCenterOf(parent)
        }
    }

    /** The way how to find Gradle logs is described here
     * @see org.jetbrains.kotlin.idea.actions.ShowKotlinGradleDslLogs.gradleTroubleshootingLink
     */
    private fun findLogsDir(): Path? = System.getProperty("user.home")?.let { userHome ->
        when {
            SystemInfo.isMac -> Path("$userHome/Library/Logs/gradle-kotlin-dsl")
            SystemInfo.isLinux -> Path("$userHome/.gradle-kotlin-dsl/logs")
            SystemInfo.isWindows -> Path("$userHome/AppData/Local/gradle-kotlin-dsl/log")
            else -> null
        }?.takeIf(Path::exists)
    }

    override fun startInWriteAction() = false

    override fun getText() = NAME

    override fun getFamilyName() = NAME

    companion object {
        private const val gradleTroubleshootingLink = "https://docs.gradle.org/current/userguide/kotlin_dsl.html#troubleshooting"

        val NAME = KotlinGradleScriptingBundle.message("action.text.show.kotlin.gradle.dsl.logs.in", RevealFileAction.getFileManagerName())
    }
}