// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.actions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractFileProcessor {
  private static final Logger LOG = Logger.getInstance(AbstractFileProcessor.class.getName());

  private final Project myProject;
  private final Module myModule;
  private final PsiFile file;
  private final PsiFile[] files;
  private final @NlsContexts.ProgressText String message;
  private final @NlsContexts.ProgressTitle String title;
  private final boolean myWithModalProgress;

  protected abstract Runnable preprocessFile(PsiFile file, boolean allowReplacement) throws IncorrectOperationException;

  protected AbstractFileProcessor(@NotNull Project project,
                                  @NotNull PsiFile file,
                                  @NotNull @NlsContexts.ProgressTitle String title,
                                  @NotNull @NlsContexts.ProgressText String message,
                                  boolean withModalProgress) {
    myProject = project;
    myModule = null;
    this.file = file;
    files = null;
    this.message = message;
    this.title = title;
    myWithModalProgress = withModalProgress;
  }

  protected AbstractFileProcessor(@NotNull Project project,
                                  PsiFile @NotNull [] files,
                                  @NotNull @NlsContexts.ProgressTitle String title,
                                  @NotNull @NlsContexts.ProgressText String message,
                                  boolean withModalProgress) {
    myProject = project;
    myModule = null;
    file = null;
    this.files = files;
    this.message = message;
    this.title = title;
    myWithModalProgress = withModalProgress;
  }

  public void run() {
    if (files != null) {
      process(files);
    }
    else if (file != null) {
      process(file);
    }
    else if (myModule != null) {
      process(myModule);
    }
    else {
      process(myProject);
    }
  }

  private void process(@NotNull PsiFile file) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(file)) return;

    execute(() -> {
      try {
        return preprocessFile(file, true);
      }
      catch (IncorrectOperationException incorrectoperationexception) {
        LOG.error(incorrectoperationexception);
        return null;
      }
    });
  }


  private Runnable prepareFiles(@NotNull List<? extends PsiFile> files) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    String msg = null;
    double fraction = 0.0D;
    if (indicator != null) {
      msg = indicator.getText();
      fraction = indicator.getFraction();
      indicator.setText(message);
    }

    final Runnable[] runnables = new Runnable[files.size()];
    for (int i = 0; i < files.size(); i++) {
      PsiFile pfile = files.get(i);
      if (pfile == null) {
        LOG.debug("Unexpected null file at " + i);
        continue;
      }
      if (indicator != null) {
        if (indicator.isCanceled()) {
          return null;
        }

        indicator.setFraction((double)i / (double)files.size());
      }

      if (pfile.isWritable()) {
        try {
          runnables[i] = preprocessFile(pfile, true);
        }
        catch (IncorrectOperationException incorrectoperationexception) {
          LOG.error(incorrectoperationexception);
        }
      }

      files.set(i, null);
    }

    if (indicator != null) {
      indicator.setText(msg);
      indicator.setFraction(fraction);
    }

    return () -> {
      ProgressIndicator indicator1 = ProgressManager.getInstance().getProgressIndicator();
      String msg1 = null;
      double fraction1 = 0.0D;
      if (indicator1 != null) {
        msg1 = indicator1.getText();
        fraction1 = indicator1.getFraction();
        indicator1.setText(message);
      }

      for (int j = 0; j < runnables.length; j++) {
        if (indicator1 != null) {
          if (indicator1.isCanceled()) {
            return;
          }

          indicator1.setFraction((double)j / (double)runnables.length);
        }

        Runnable runnable = runnables[j];
        if (runnable != null) {
          runnable.run();
        }
        runnables[j] = null;
      }

      if (indicator1 != null) {
        indicator1.setText(msg1);
        indicator1.setFraction(fraction1);
      }
    };
  }

  private void process(final PsiFile @NotNull [] files) {
    execute(() -> prepareFiles(new ArrayList<>(Arrays.asList(files))));
  }

  private void process(@NotNull Project project) {
    final List<PsiFile> pfiles = new ArrayList<>();
    runWithProgress(() -> findFiles(project, pfiles));
    handleFiles(pfiles);
  }

  private void process(@NotNull Module module) {
    final List<PsiFile> pfiles = new ArrayList<>();
    runWithProgress(() -> findFiles(module, pfiles));
    handleFiles(pfiles);
  }

  private static void findFiles(@NotNull Project project, List<? super PsiFile> files) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      findFiles(module, files);
    }
  }

  private static void findFiles(@NotNull Module module, @NotNull List<? super PsiFile> files) {
    final ModuleFileIndex idx = ModuleRootManager.getInstance(module).getFileIndex();

    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();

    for (final VirtualFile root : roots) {
      ApplicationManager.getApplication().runReadAction(() -> {
        idx.iterateContentUnderDirectory(root, dir -> {
          if (dir.isDirectory()) {
            final PsiDirectory psiDir = PsiManager.getInstance(module.getProject()).findDirectory(dir);
            if (psiDir != null) {
              findFiles(files, psiDir, false);
            }
          }
          return true;
        });
      });
    }
  }

  private void handleFiles(@NotNull List<? extends PsiFile> files) {
    List<VirtualFile> list = new ArrayList<>();
    for (PsiFile psiFile : files) {
      list.add(psiFile.getVirtualFile());
    }
    if (!ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(list).hasReadonlyFiles()) {
      if (!files.isEmpty()) {
        execute(() -> prepareFiles(files));
      }
    }
  }

  private static void findFiles(@NotNull List<? super PsiFile> files, @NotNull PsiDirectory directory, boolean subdirs) {
    final Project project = directory.getProject();
    PsiFile[] locals = directory.getFiles();
    for (PsiFile local : locals) {
      CopyrightProfile opts = CopyrightManager.getInstance(project).getCopyrightOptions(local);
      if (opts != null && FileTypeUtil.isSupportedFile(local)) {
        files.add(local);
      }
    }

    if (subdirs) {
      for (PsiDirectory dir : directory.getSubdirectories()) {
        findFiles(files, dir, true);
      }
    }
  }

  private void runWithProgress(@NotNull Runnable action) {
    if (myWithModalProgress) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(action, title, true, myProject);
    }
    else {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
      action.run();
    }
  }

  private void execute(@NotNull Computable<Runnable> readAction) {
    final Runnable[] writeAction = new Runnable[1];
    runWithProgress(() -> ApplicationManager.getApplication().runReadAction(() -> {
      writeAction[0] = readAction.compute();
    }));
    if (writeAction[0] != null) {
      WriteCommandAction.writeCommandAction(myProject).withName(title).run(() -> {
        writeAction[0].run();
      });
    }
  }
}
