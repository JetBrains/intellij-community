/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maddyhome.idea.copyright.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.ui.CopyrightProjectConfigurable;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nullable;

public class GenerateCopyrightAction extends AnAction
{
    public void update(AnActionEvent event)
    {
        Presentation presentation = event.getPresentation();
        DataContext context = event.getDataContext();
        Project project = CommonDataKeys.PROJECT.getData(context);
        if (project == null)
        {
            presentation.setEnabled(false);
            return;
        }

        PsiFile file = getFile(context, project);
        if (file == null || !FileTypeUtil.isSupportedFile(file)) {
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

    public void actionPerformed(AnActionEvent event)
    {
        DataContext context = event.getDataContext();
        Project project = CommonDataKeys.PROJECT.getData(context);
        assert project != null;
        Module module = LangDataKeys.MODULE.getData(context);
        PsiDocumentManager.getInstance(project).commitAllDocuments();


        PsiFile file = getFile(context, project);
        assert file != null;
        if (CopyrightManager.getInstance(project).getCopyrightOptions(file) == null) {
          if (Messages.showOkCancelDialog(project, "No copyright configured for current file. Would you like to edit copyright settings?", "No Copyright Available", Messages.getQuestionIcon()) == Messages.OK) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, new CopyrightProjectConfigurable(project).getDisplayName());
          } else {
            return;
          }
        }
        new UpdateCopyrightProcessor(project, module, file).run();
    }
}