// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.status.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgUpdater;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.status.HgCurrentBranchStatus;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class HgCurrentBranchStatusUpdater implements HgUpdater {

  private final HgVcs vcs;
  private final HgCurrentBranchStatus currentBranchStatus;

  private MessageBusConnection busConnection;

  public HgCurrentBranchStatusUpdater(HgVcs vcs, HgCurrentBranchStatus currentBranchStatus) {
    this.vcs = vcs;
    this.currentBranchStatus = currentBranchStatus;
  }

  @Override
  public void update(final Project project, @Nullable VirtualFile root) {
    update(project);
  }

  public void update(final Project project) {
    final AtomicReference<Editor> textEditor = new AtomicReference<Editor>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            textEditor.set(FileEditorManager.getInstance(project).getSelectedTextEditor());
          }
        });
      }
    });

    if (textEditor.get() == null) {
      handleUpdate(project, null, Collections.<HgRevisionNumber>emptyList());
    }
    else {
      if (project.isDisposed()) {
        return;
      }
      Document document = textEditor.get().getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(document);

      final VirtualFile repo = VcsUtil.getVcsRootFor(project, file);
      if (repo != null) {
        ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            HgTagBranchCommand hgTagBranchCommand = new HgTagBranchCommand(project, repo);
            final String branch = hgTagBranchCommand.getCurrentBranch();
            final List<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(project).parents(repo);
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                handleUpdate(project, branch, parents);
              }
            });
          }
        });
      }
    }
  }

  private void handleUpdate(@NotNull Project project, @Nullable String branch, @NotNull List<HgRevisionNumber> parents) {
    currentBranchStatus.updateFor(branch, parents);
    if (!project.isDisposed()) {
      project.getMessageBus().syncPublisher(HgVcs.STATUS_TOPIC).update(project, null);
    }
  }

  public void activate() {
    busConnection = vcs.getProject().getMessageBus().connect();
    busConnection.subscribe(HgVcs.BRANCH_TOPIC, this);
  }

  public void deactivate() {
    busConnection.disconnect();
  }
}
