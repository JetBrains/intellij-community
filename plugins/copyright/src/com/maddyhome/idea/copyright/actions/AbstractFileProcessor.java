/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractFileProcessor {
  private final Project myProject;
  private final Module myModule;
  private PsiDirectory directory = null;
  private PsiFile file = null;
  private PsiFile[] files = null;
  private boolean subdirs = false;
  private final String message;
  private final String title;

  protected abstract Runnable preprocessFile(PsiFile file, boolean allowReplacement) throws IncorrectOperationException;

  protected AbstractFileProcessor(Project project, String title, String message) {
    myProject = project;
    myModule = null;
    directory = null;
    subdirs = true;
    this.title = title;
    this.message = message;
  }

  protected AbstractFileProcessor(Project project, Module module, String title, String message) {
    myProject = project;
    myModule = module;
    directory = null;
    subdirs = true;
    this.title = title;
    this.message = message;
  }

  protected AbstractFileProcessor(Project project, PsiDirectory dir, boolean subdirs, String title, String message) {
    myProject = project;
    myModule = null;
    directory = dir;
    this.subdirs = subdirs;
    this.message = message;
    this.title = title;
  }

  protected AbstractFileProcessor(Project project, PsiFile file, String title, String message) {
    myProject = project;
    myModule = null;
    this.file = file;
    this.message = message;
    this.title = title;
  }

  protected AbstractFileProcessor(Project project, PsiFile[] files, String title, String message, Runnable runnable) {
    myProject = project;
    myModule = null;
    this.files = files;
    this.message = message;
    this.title = title;
  }

  public void run() {
    if (directory != null) {
      process(directory, subdirs);
    }
    else if (files != null) {
      process(files);
    }
    else if (file != null) {
      process(file);
    }
    else if (myModule != null) {
      process(myModule);
    }
    else if (myProject != null) {
      process(myProject);
    }
  }

  private void process(final PsiFile file) {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(file)) return;
    final Runnable[] resultRunnable = new Runnable[1];

    execute(() -> {
      try {
        resultRunnable[0] = preprocessFile(file, true);
      }
      catch (IncorrectOperationException incorrectoperationexception) {
        logger.error(incorrectoperationexception);
      }
    }, () -> {
      if (resultRunnable[0] != null) {
        resultRunnable[0].run();
      }
    });
  }


  private Runnable prepareFiles(List<PsiFile> files) {
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
        logger.debug("Unexpected null file at " + i);
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
          logger.error(incorrectoperationexception);
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

  private void process(final PsiFile[] files) {
    final Runnable[] resultRunnable = new Runnable[1];
    execute(() -> resultRunnable[0] = prepareFiles(new ArrayList<>(Arrays.asList(files))), () -> {
      if (resultRunnable[0] != null) {
        resultRunnable[0].run();
      }
    });
  }

  private void process(final PsiDirectory dir, final boolean subdirs) {
    final List<PsiFile> pfiles = new ArrayList<>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> findFiles(pfiles, dir, subdirs), title, true, myProject);
    handleFiles(pfiles);
  }

  private void process(final Project project) {
    final List<PsiFile> pfiles = new ArrayList<>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> findFiles(project, pfiles), title, true, project);
    handleFiles(pfiles);
  }

  private void process(final Module module) {
    final List<PsiFile> pfiles = new ArrayList<>();
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> findFiles(module, pfiles), title, true, myProject);
    handleFiles(pfiles);
  }

  private static void findFiles(Project project, List<PsiFile> files) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      findFiles(module, files);
    }

  }

  protected static void findFiles(final Module module, final List<PsiFile> files) {
    final ModuleFileIndex idx = ModuleRootManager.getInstance(module).getFileIndex();

    final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();

    for (final VirtualFile root : roots) {
      ApplicationManager.getApplication().runReadAction(() -> {
        idx.iterateContentUnderDirectory(root, new ContentIterator() {
          @Override
          public boolean processFile(final VirtualFile dir) {
            if (dir.isDirectory()) {
              final PsiDirectory psiDir = PsiManager.getInstance(module.getProject()).findDirectory(dir);
              if (psiDir != null) {
                findFiles(files, psiDir, false);
              }
            }
            return true;
          }
        });
      });
    }
  }

  private void handleFiles(final List<PsiFile> files) {
    final List<VirtualFile> vFiles = new ArrayList<>();
    for (PsiFile psiFile : files) {
      vFiles.add(psiFile.getVirtualFile());
    }
    if (!ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(VfsUtil.toVirtualFileArray(vFiles))
      .hasReadonlyFiles()) {
      if (!files.isEmpty()) {
        final Runnable[] resultRunnable = new Runnable[1];
        execute(() -> resultRunnable[0] = prepareFiles(files), () -> {
          if (resultRunnable[0] != null) {
            resultRunnable[0].run();
          }
        });
      }
    }
  }

  private static void findFiles(List<PsiFile> files, PsiDirectory directory, boolean subdirs) {
    final Project project = directory.getProject();
    PsiFile[] locals = directory.getFiles();
    for (PsiFile local : locals) {
      CopyrightProfile opts = CopyrightManager.getInstance(project).getCopyrightOptions(local);
      if (opts != null && FileTypeUtil.isSupportedFile(local)) {
        files.add(local);
      }

    }

    if (subdirs) {
      PsiDirectory[] dirs = directory.getSubdirectories();
      for (PsiDirectory dir : dirs) {
        findFiles(files, dir, subdirs);
      }

    }
  }

  private void execute(final Runnable readAction, final Runnable writeAction) {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> ApplicationManager.getApplication().runReadAction(() -> readAction.run()), title, true, myProject);
    new WriteCommandAction(myProject, title) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        writeAction.run();
      }
    }.execute();
  }

  private static final Logger logger = Logger.getInstance(AbstractFileProcessor.class.getName());
}
