// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.uiDesigner

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.LightColors
import com.intellij.uiDesigner.editor.UIFormEditor
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.function.Function
import javax.swing.JComponent

private class ConvertFormNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!PsiUtil.isIdeaProject(project)) {
      return null
    }

    return Function { fileEditor ->
      if (fileEditor !is UIFormEditor) {
        return@Function null
      }

      val formPsiFile = PsiManager.getInstance(project).findFile(file) ?: return@Function null
      val classToBind = fileEditor.editor.rootContainer.classToBind ?: return@Function null
      val psiClass = JavaPsiFacade.getInstance(project).findClass(classToBind, ProjectScope.getProjectScope(project))
                     ?: return@Function null

      val panel = EditorNotificationPanel(LightColors.RED, EditorNotificationPanel.Status.Error)
      panel.text = DevKitUIDesignerBundle.message("convert.form.editor.notification.label")
      /* todo IDEA-282478
    createActionLabel(DevKitBundle.message("convert.form.editor.notification.link.convert")) {
      convertFormToUiDsl(psiClass, formPsiFile)
    }
    */
      panel
    }
  }
}
