// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class UpdateCopyrightAction extends BaseAnalysisAction {
  public static final String UPDATE_EXISTING_COPYRIGHTS = "update.existing.copyrights";
  private UpdateCopyrightAdditionalUi myUi;

  private UpdateCopyrightAction() {
    super(UpdateCopyrightProcessor.TITLE, UpdateCopyrightProcessor.TITLE);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final boolean enabled = isEnabled(event);
    event.getPresentation().setEnabled(enabled);
    if (event.isFromContextMenu()) {
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
             PlatformCoreDataKeys.PROJECT_CONTEXT.getData(context) == null) {
      final PsiElement[] elems = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(context);
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

  @Override
  protected @NotNull JComponent getAdditionalActionSettings(@NotNull Project project, BaseAnalysisActionDialog dialog) {
    myUi = new UpdateCopyrightAdditionalUi();
    myUi.getUpdateExistingCopyrightsCb().setSelected(PropertiesComponent.getInstance().getBoolean(UPDATE_EXISTING_COPYRIGHTS, true));
    return myUi.getPanel();
  }

  @Override
  protected void analyze(final @NotNull Project project, final @NotNull AnalysisScope scope) {
    PropertiesComponent.getInstance().setValue(UPDATE_EXISTING_COPYRIGHTS, String.valueOf(myUi.getUpdateExistingCopyrightsCb().isSelected()), "true");
    Task.Backgroundable task = new UpdateCopyrightTask(project, scope, myUi.getUpdateExistingCopyrightsCb().isSelected(), PerformInBackgroundOption.ALWAYS_BACKGROUND);
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

  public static class UpdateCopyrightTask extends Task.ConditionalModal {
    private final Map<PsiFile, Runnable> preparations = new LinkedHashMap<>();
    private final @NotNull AnalysisScope myScope;
    private final boolean myAllowReplacement;

    public UpdateCopyrightTask(@NotNull Project project,
                               @NotNull AnalysisScope scope,
                               boolean allowReplacement,
                               @NotNull PerformInBackgroundOption options) {
      super(project, CopyrightBundle.message("task.title.prepare.copyright"), true, options);
      myScope = scope;
      myAllowReplacement = allowReplacement;
    }

    @Override
    public void run(final @NotNull ProgressIndicator indicator) {
      myScope.accept(new PsiElementVisitor() {
        @Override
        public void visitFile(final @NotNull PsiFile psiFile) {
          if (indicator.isCanceled()) {
            return;
          }
          final Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
          final UpdateCopyrightProcessor processor = new UpdateCopyrightProcessor(psiFile.getProject(), module, psiFile);
          
          final Runnable runnable = processor.preprocessFile(psiFile, myAllowReplacement);
          if (runnable != EmptyRunnable.getInstance()) {
            preparations.put(psiFile, runnable);
          }
        }
      });
    }

    @Override
    public void onSuccess() {
      if (!preparations.isEmpty()) {
        if (!FileModificationService.getInstance().preparePsiElementsForWrite(preparations.keySet())) return;
        final SequentialModalProgressTask
          progressTask = new SequentialModalProgressTask(myProject, UpdateCopyrightProcessor.TITLE.get(), true);
        progressTask.setMinIterationTime(200);
        progressTask.setTask(new UpdateCopyrightSequentialTask(preparations, progressTask));
        CommandProcessor.getInstance().executeCommand(myProject, () -> {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(myProject);
          ProgressManager.getInstance().run(progressTask);
        }, UpdateCopyrightProcessor.TITLE.get(), null);
      }
    }
  }
}