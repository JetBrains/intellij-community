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
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgUtil;

import java.lang.reflect.InvocationTargetException;

import static org.zmlx.hg4idea.HgErrorHandler.ensureSuccess;

public final class HgHeadMerger {

  private static final Logger LOG = Logger.getInstance(HgHeadMerger.class.getName());
  private final Project project;
  private final HgMergeCommand hgMergeCommand;

  public HgHeadMerger(Project project, @NotNull HgMergeCommand hgMergeCommand) {
    this.project = project;
    this.hgMergeCommand = hgMergeCommand;
  }

  public HgCommandResult merge(VirtualFile repo) throws VcsException {

    HgCommandResult commandResult = ensureSuccess(hgMergeCommand.execute());
    try {
      HgUtil.markDirectoryDirty(project, repo);
    }
    catch (InvocationTargetException e) {
      throwException(e);
    }
    catch (InterruptedException e) {
      throwException(e);
    }

    return commandResult;
  }

  private static void throwException(Exception e) throws VcsException {
    String msg = "Exception during marking directory dirty: " + e;
    LOG.info(msg, e);
    throw new VcsException(msg);
  }
}
