// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.actions;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import com.intellij.openapi.vcs.checkin.CheckinModificationHandler;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class UpdateCopyrightCheckinHandlerFactory extends CheckinHandlerFactory {
  @Override
  @NotNull
  public CheckinHandler createHandler(@NotNull final CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    return new UpdateCopyrightCheckinHandler(panel);
  }

  private static class UpdateCopyrightCheckinHandler extends CheckinHandler implements CheckinModificationHandler {
    private final @NotNull CheckinProjectPanel myPanel;

    UpdateCopyrightCheckinHandler(@NotNull CheckinProjectPanel panel) {
      myPanel = panel;
    }

    @Override
    public RefreshableOnComponent getBeforeCheckinConfigurationPanel() {
      return new BooleanCommitOption(myPanel, CopyrightBundle.message("before.checkin.update.copyright"), false, () -> getSettings().UPDATE_COPYRIGHT,
                                     value -> getSettings().UPDATE_COPYRIGHT = value);
    }

    @Override
    public ReturnResult beforeCheckin(@Nullable CommitExecutor executor, PairConsumer<Object, Object> additionalDataConsumer) {
      if (getSettings().UPDATE_COPYRIGHT) {
        new UpdateCopyrightProcessor(myPanel.getProject(), null, getPsiFiles()).run();
        FileDocumentManager.getInstance().saveAllDocuments();
      }
      return super.beforeCheckin();
    }

    @NotNull
    private UpdateCopyrightCheckinHandlerState getSettings() {
      return UpdateCopyrightCheckinHandlerState.getInstance(myPanel.getProject());
    }

    private PsiFile @NotNull [] getPsiFiles() {
      final Collection<VirtualFile> files = myPanel.getVirtualFiles();
      final List<PsiFile> psiFiles = new ArrayList<>();
      final PsiManager manager = PsiManager.getInstance(myPanel.getProject());
      for (final VirtualFile file : files) {
        final PsiFile psiFile = manager.findFile(file);
        if (psiFile != null) {
          psiFiles.add(psiFile);
        }
      }
      return PsiUtilCore.toPsiFileArray(psiFiles);
    }
  }
}