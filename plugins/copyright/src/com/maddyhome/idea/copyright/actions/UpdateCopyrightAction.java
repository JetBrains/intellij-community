// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateCopyrightAction extends BaseAnalysisAction {
  public static final String UPDATE_EXISTING_COPYRIGHTS = "update.existing.copyrights";
  private JCheckBox myUpdateExistingCopyrightsCb;

  protected UpdateCopyrightAction() {
    super(UpdateCopyrightProcessor.TITLE, UpdateCopyrightProcessor.TITLE);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final boolean enabled = isEnabled(event);
    event.getPresentation().setEnabled(enabled);
    if (ActionPlaces.isPopupPlace(event.getPlace())) {
      event.getPresentation().setVisible(enabled);
    }
  }

  private static boolean isEnabled(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Project project = e.getProject();
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
      return FileTypeUtil.isSupportedFile(file);
    }
    if (files != null && areFiles(files)) {
      boolean copyrightEnabled  = false;
      for (VirtualFile vfile : files) {
        if (vfile != null && FileTypeUtil.isSupportedFile(vfile)) {
          copyrightEnabled = true;
          break;
        }
      }
      return copyrightEnabled;
    }
    if ((files == null || files.length != 1) &&
             LangDataKeys.MODULE_CONTEXT.getData(context) == null &&
             LangDataKeys.MODULE_CONTEXT_ARRAY.getData(context) == null &&
             PlatformDataKeys.PROJECT_CONTEXT.getData(context) == null) {
      final PsiElement[] elems = LangDataKeys.PSI_ELEMENT_ARRAY.getData(context);
      if (elems != null) {
        boolean copyrightEnabled = false;
        for (PsiElement elem : elems) {
          if (!(elem instanceof PsiDirectory)) {
            final PsiFile file = elem.getContainingFile();
            if (file == null || !FileTypeUtil.isSupportedFile(file.getVirtualFile())) {
              copyrightEnabled = true;
              break;
            }
          }
        }
        return copyrightEnabled;
      }
    }
    return true;
  }

  @Nullable
  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(new TitledSeparator());
    myUpdateExistingCopyrightsCb = new JCheckBox(CopyrightBundle.message("checkbox.text.update.existing.copyrights"),
                                                 PropertiesComponent.getInstance().getBoolean(UPDATE_EXISTING_COPYRIGHTS, true));
    panel.add(myUpdateExistingCopyrightsCb);
    return panel;
  }

  @Override
  protected void analyze(@NotNull final Project project, @NotNull final AnalysisScope scope) {
    PropertiesComponent.getInstance().setValue(UPDATE_EXISTING_COPYRIGHTS, String.valueOf(myUpdateExistingCopyrightsCb.isSelected()), "true");
    final Map<PsiFile, Runnable> preparations = new LinkedHashMap<>();
    Task.Backgroundable task = new Task.Backgroundable(project, CopyrightBundle.message("task.title.prepare.copyright"), true) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        scope.accept(new PsiElementVisitor() {
          @Override
          public void visitFile(@NotNull final PsiFile file) {
            if (indicator.isCanceled()) {
              return;
            }
            final Module module = ModuleUtilCore.findModuleForPsiElement(file);
            final UpdateCopyrightProcessor processor = new UpdateCopyrightProcessor(project, module, file);
            final Runnable runnable = processor.preprocessFile(file, myUpdateExistingCopyrightsCb.isSelected());
            if (runnable != EmptyRunnable.getInstance()) {
              preparations.put(file, runnable);
            }
          }
        });
      }

      @Override
      public void onSuccess() {
        if (!preparations.isEmpty()) {
          if (!FileModificationService.getInstance().preparePsiElementsForWrite(preparations.keySet())) return;
          final SequentialModalProgressTask progressTask = new SequentialModalProgressTask(project, UpdateCopyrightProcessor.TITLE, true);
          progressTask.setMinIterationTime(200);
          progressTask.setTask(new UpdateCopyrightSequentialTask(preparations, progressTask));
          CommandProcessor.getInstance().executeCommand(project, () -> {
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
            ProgressManager.getInstance().run(progressTask);
          }, getTemplatePresentation().getText(), null);
        }
      }
    };

    ProgressManager.getInstance().run(task);
  }

  private static final class UpdateCopyrightSequentialTask implements SequentialTask {
    private final int mySize;
    private final Iterator<Runnable> myRunnables;
    private final SequentialModalProgressTask myProgressTask;
    private int myIdx;

    private UpdateCopyrightSequentialTask(Map<PsiFile, Runnable> runnables, SequentialModalProgressTask progressTask) {
      myRunnables = runnables.values().iterator();
      myProgressTask = progressTask;
      mySize = runnables.size();
    }

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
      myRunnables.next().run();
      myIdx++;
      return true;
    }

    @Override
    public void stop() {
      myIdx = mySize;
    }
  }

  private static boolean areFiles(VirtualFile @NotNull [] files) {
    if (files.length < 2) {
      return false;
    }

    for (VirtualFile file : files) {
      if (file.isDirectory()) {
        return false;
      }
    }

    return true;
  }
}