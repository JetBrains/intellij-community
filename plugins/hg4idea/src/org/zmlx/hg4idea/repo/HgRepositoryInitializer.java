// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.repo;

import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.vcs.VcsRepositoryInitializer;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgInitCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.io.File;

public class HgRepositoryInitializer implements VcsRepositoryInitializer {
  @Override
  public void initRepository(@NotNull File rootDir) throws VcsException {
    HgCommandResult result = new HgInitCommand(ProjectManager.getInstance().getDefaultProject()).execute(rootDir.getPath());
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      throw new VcsException(result.getRawError());
    }
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return HgVcs.getKey();
  }
}
