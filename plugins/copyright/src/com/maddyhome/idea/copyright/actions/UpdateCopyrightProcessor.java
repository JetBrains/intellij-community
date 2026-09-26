// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.actions;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory;
import com.maddyhome.idea.copyright.psi.UpdatePsiFileCopyright;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class UpdateCopyrightProcessor extends AbstractFileProcessor {
  public static final @NotNull Supplier<@Nls String> TITLE = CopyrightBundle.messagePointer("update.copyright.progress.title");
  public static final @NotNull Supplier<@Nls String> MESSAGE = CopyrightBundle.messagePointer("updating.copyrights.progress.message");

  public UpdateCopyrightProcessor(@NotNull Project project, Module module, @NotNull PsiFile file) {
    super(project, file, TITLE.get(), MESSAGE.get(), true);
    setup(project, module);
  }

  public UpdateCopyrightProcessor(@NotNull Project project, Module module, PsiFile @NotNull [] files) {
    this(project, module, files, true);
  }

  public UpdateCopyrightProcessor(@NotNull Project project, Module module, PsiFile @NotNull [] files, boolean withModalProgress) {
    super(project, files, TITLE.get(), MESSAGE.get(), withModalProgress);
    setup(project, module);
  }

  @Override
  protected Runnable preprocessFile(final PsiFile file, final boolean allowReplacement) throws IncorrectOperationException {
    VirtualFile vfile = file.getVirtualFile();
    if (vfile == null) return EmptyRunnable.getInstance();
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setText2(vfile.getPresentableUrl());
    }
    Module mod = module;
    if (module == null) {
      mod = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vfile);
    }

    if (mod == null) return EmptyRunnable.getInstance();

    CopyrightProfile opts = CopyrightManager.getInstance(project).getCopyrightOptions(file);

    if (opts != null && FileTypeUtil.isSupportedFile(file)) {
      logger.debug("process " + file);
      final UpdateCopyright update = UpdateCopyrightFactory.createUpdateCopyright(project, mod, file, opts);
      if (update == null) return EmptyRunnable.getInstance();
      update.prepare();

      if (update instanceof UpdatePsiFileCopyright && !((UpdatePsiFileCopyright)update).hasUpdates()) return EmptyRunnable.getInstance();

      return () -> {
        try {
          if (update instanceof UpdatePsiFileCopyright) {
            ((UpdatePsiFileCopyright)update).complete(allowReplacement);
          }
          else {
            update.complete();
          }
        }
        catch (Exception e) {
          logger.error(e);
        }
      };
    }
    else {
      return EmptyRunnable.getInstance();
    }
  }

  private void setup(Project project, Module module) {
    this.project = project;
    this.module = module;
  }

  private Project project;
  private Module module;

  private static final Logger logger = Logger.getInstance(UpdateCopyrightProcessor.class.getName());
}
