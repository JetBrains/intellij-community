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

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.pattern.FileUtil;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class UpdateCopyrightAction extends BaseAnalysisAction {
  public static final String UPDATE_EXISTING_COPYRIGHTS = "update.existing.copyrights";
  private JCheckBox myUpdateExistingCopyrightsCb;

  protected UpdateCopyrightAction() {
    super(UpdateCopyrightProcessor.TITLE, UpdateCopyrightProcessor.TITLE);
  }

  public void update(AnActionEvent event) {
    final boolean enabled = isEnabled(event);
    event.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled(AnActionEvent event) {
    final DataContext context = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return false;
    }

    if (!CopyrightManager.getInstance(project).hasAnyCopyrights()) {
      return false;
    }
    final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
    final Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null || !FileTypeUtil.isSupportedFile(file)) {
        return false;
      }
    }
    else if (files != null && FileUtil.areFiles(files)) {
      boolean copyrightEnabled  = false;
      for (VirtualFile vfile : files) {
        if (FileTypeUtil.getInstance().isSupportedFile(vfile)) {
          copyrightEnabled = true;
          break;
        }
      }
      if (!copyrightEnabled) {
        return false;
      }

    }
    else if ((files == null || files.length != 1) &&
             LangDataKeys.MODULE_CONTEXT.getData(context) == null &&
             LangDataKeys.MODULE_CONTEXT_ARRAY.getData(context) == null &&
             PlatformDataKeys.PROJECT_CONTEXT.getData(context) == null) {
      final PsiElement[] elems = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
      if (elems != null) {
        boolean copyrightEnabled = false;
        for (PsiElement elem : elems) {
          if (!(elem instanceof PsiDirectory)) {
            final PsiFile file = elem.getContainingFile();
            if (file == null || !FileTypeUtil.getInstance().isSupportedFile(file.getVirtualFile())) {
              copyrightEnabled = true;
              break;
            }
          }
        }
        if (!copyrightEnabled){
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(new TitledSeparator());
    myUpdateExistingCopyrightsCb = new JCheckBox("Update existing copyrights", 
                                                 PropertiesComponent.getInstance().getBoolean(UPDATE_EXISTING_COPYRIGHTS, true));
    panel.add(myUpdateExistingCopyrightsCb);
    return panel;
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    PropertiesComponent.getInstance().setValue(UPDATE_EXISTING_COPYRIGHTS, String.valueOf(myUpdateExistingCopyrightsCb.isSelected()));
    if (scope.checkScopeWritable(project)) return;
    final List<Runnable> preparations = new ArrayList<Runnable>();
    Task.Backgroundable task = new Task.Backgroundable(project, "Prepare Copyright...", true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        scope.accept(new PsiElementVisitor() {
          @Override
          public void visitFile(final PsiFile file) {
            if (indicator.isCanceled()) {
              return;
            }
            preparations.add(new UpdateCopyrightProcessor(project, ModuleUtilCore.findModuleForPsiElement(file), file).preprocessFile(file, myUpdateExistingCopyrightsCb.isSelected()));
          }
        });
      }

      @Override
      public void onSuccess() {
        if (!preparations.isEmpty()) {
          final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, UpdateCopyrightProcessor.TITLE, true);
          progressTask.setMinIterationTime(200);
          progressTask.setTask(new UpdateCopyrightSequentialTask(preparations, progressTask));
          CommandProcessor.getInstance().executeCommand(project, new Runnable() {
            @Override
            public void run() {
              CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                @Override
                public void run() {
                  ProgressManager.getInstance().run(progressTask);
                }
              });
            }
          }, getTemplatePresentation().getText(), null);
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }

  private static class UpdateCopyrightSequentialTask implements SequentialTask {
    private final int mySize;
    private final List<Runnable> myRunnables;
    private final SequentialModalProgressTask myProgressTask;
    private int myIdx = 0;

    private UpdateCopyrightSequentialTask(List<Runnable> runnables, SequentialModalProgressTask progressTask) {
      myRunnables = runnables;
      myProgressTask = progressTask;
      mySize = myRunnables.size();
    }

    @Override
    public void prepare() {}

    @Override
    public boolean isDone() {
      return myIdx > mySize - 1;
    }

    @Override
    public boolean iteration() {
      final ProgressIndicator indicator = myProgressTask.getIndicator();
      if (indicator != null) {
        indicator.setFraction((double) myIdx/mySize);
      }
      myRunnables.get(myIdx++).run();
      return true;
    }

    @Override
    public void stop() {
      myIdx = mySize;
    }
  }
}