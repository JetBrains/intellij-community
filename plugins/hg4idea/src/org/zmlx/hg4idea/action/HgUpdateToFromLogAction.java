// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.repo.HgRepository;

public class HgUpdateToFromLogAction extends HgLogSingleCommitAction {
  @Override
  protected void actionPerformed(final @NotNull HgRepository repository, @NotNull Hash commit) {
    final Project project = repository.getProject();
    final VirtualFile root = repository.getRoot();
    FileDocumentManager.getInstance().saveAllDocuments();
    HgUpdateCommand.updateRepoTo(project, root, commit.asString(), null);
  }
}
