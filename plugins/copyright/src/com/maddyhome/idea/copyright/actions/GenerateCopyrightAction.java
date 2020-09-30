// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.actions;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.ui.CopyrightProjectConfigurable;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GenerateCopyrightAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext context = e.getDataContext();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    PsiFile file = getFile(context, project);
    if (!FileTypeUtil.isSupportedFile(file)) {
      presentation.setEnabled(false);
    }
  }

  @Nullable
  private static PsiFile getFile(DataContext context, Project project) {
    PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
    if (file == null) {
      Editor editor = CommonDataKeys.EDITOR.getData(context);
      if (editor != null) {
        file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      }
    }
    return file;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext context = e.getDataContext();
    Project project = e.getProject();
    assert project != null;
    Module module = e.getData(LangDataKeys.MODULE);
    PsiDocumentManager.getInstance(project).commitAllDocuments();


    PsiFile file = getFile(context, project);
    assert file != null;
    if (CopyrightManager.getInstance(project).getCopyrightOptions(file) == null) {
      if (Messages.showOkCancelDialog(project, CopyrightBundle.message("dialog.message.no.copyright.configured"),
                                      CopyrightBundle.message("dialog.title.no.copyright.available"), Messages.getQuestionIcon()) == Messages.OK) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, new CopyrightProjectConfigurable(project).getDisplayName());
      }
      else {
        return;
      }
    }
    new UpdateCopyrightProcessor(project, module, file).run();
  }
}