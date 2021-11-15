// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.formConversion

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import com.intellij.uiDesigner.editor.UIFormEditor
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil


class ConvertFormNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  companion object {
    private val KEY = Key.create<EditorNotificationPanel>("convert.form.notification.panel")
  }

  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (fileEditor !is UIFormEditor) return null
    if (!PsiUtil.isIdeaProject(project)) return null

    val formPsiFile = PsiManager.getInstance(project).findFile(file) ?: return null
    val classToBind = fileEditor.editor.rootContainer.classToBind ?: return null
    val psiClass = JavaPsiFacade.getInstance(project).findClass(classToBind, ProjectScope.getProjectScope(project)) ?: return null

    return EditorNotificationPanel(LightColors.RED).apply {
      setText(DevKitBundle.message("convert.form.editor.notification.label"))
      /* todo IDEA-282478
      createActionLabel(DevKitBundle.message("convert.form.editor.notification.link.convert")) {
        convertFormToUiDsl(psiClass, formPsiFile)
      }
      */
    }
  }
}
