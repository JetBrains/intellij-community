// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.uiDesigner

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.LightColors
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.uiDesigner.editor.UIFormEditor
import com.intellij.util.ui.JBUI
import org.jetbrains.idea.devkit.util.PsiUtil
import java.util.function.Function
import javax.swing.JComponent

private class ConvertFormNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!PsiUtil.isIdeaProject(project)) {
      return null
    }
    val formPsiFile = PsiManager.getInstance(project).findFile(file) ?: return null

    return Function { fileEditor ->
      if (fileEditor !is UIFormEditor) {
        return@Function null
      }
      val classToBind = fileEditor.editor.rootContainer.classToBind ?: return@Function null

      /* todo IDEA-282478
      val psiClass = JavaPsiFacade.getInstance(project).findClass(classToBind, ProjectScope.getProjectScope(project))
                     ?: return@Function null
    createActionLabel(DevKitBundle.message("convert.form.editor.notification.link.convert")) {
      convertFormToUiDsl(psiClass, formPsiFile)
    }
    */
      panel {
        row {
          icon(AllIcons.General.BalloonError)
            .align(AlignY.TOP)
            .gap(RightGap.SMALL)
          text(DevKitUIDesignerBundle.message("convert.form.editor.notification.label"),
               maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
        }
      }.apply {
        border = JBUI.Borders.empty(JBUI.CurrentTheme.Editor.Notification.borderInsets())
        background = LightColors.RED
      }
    }
  }
}
