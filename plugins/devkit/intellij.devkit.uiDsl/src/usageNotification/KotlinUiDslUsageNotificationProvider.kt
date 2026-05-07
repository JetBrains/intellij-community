// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.usageNotification

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent

private const val UI_DSL_PACKAGE = "com.intellij.ui.dsl"
private val NOTIFICATION_ALLOWED_KEY = Key.create<Boolean>("KotlinUiDslUsageNotificationProvider.notificationAllowed")

internal class KotlinUiDslUsageNotificationProvider : EditorNotificationProvider, DumbAware {

  private val isNotificationEnabled: Boolean
    get() = Registry.`is`("devkit.uiDsl.usage.notification.enabled")

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    if (!isNotificationEnabled || file.extension != "kt") {
      return null
    }

    val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null
    val notificationAllowed = file.getOrCreateUserData(NOTIFICATION_ALLOWED_KEY) {
      !isUiDslUsed(ktFile)
    }

    if (!(notificationAllowed && isUiDslUsed(ktFile))) {
      return null
    }

    return Function { editor ->
      file.putUserData(NOTIFICATION_ALLOWED_KEY, false)

      EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info).apply {
        text = DevkitUiDslBundle.message("kotlin.ui.dsl.usage.notification")
      }
    }
  }

  private fun isUiDslUsed(file: KtFile): Boolean {
    return file.importDirectives.any {
      it.importPath?.pathStr?.startsWith(UI_DSL_PACKAGE) == true
    }
  }
}
