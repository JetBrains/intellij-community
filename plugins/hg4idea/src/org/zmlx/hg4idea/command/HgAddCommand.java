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
package org.zmlx.hg4idea.command;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A wrapper for 'hg add' command.
 */
public class HgAddCommand {

  private final Project myProject;

  public HgAddCommand(Project project) {
    myProject = project;
  }

  /**
   * Adds given files to their Mercurial repositories.
   * @param files files to be added.
   */
  public void executeInCurrentThread(@NotNull Collection<VirtualFile> files) {
    executeInCurrentThread(files, null);
  }

  public void addWithProgress(final Collection<VirtualFile> files) {
    if (files.size() >= HgUtil.MANY_FILES) {
      new Task.Backgroundable(myProject, "Adding Files to Mercurial", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(false);
          executeInCurrentThread(files, indicator);
        }
      }.queue();
    }
    else {
      BackgroundTaskUtil.executeOnPooledThread(myProject, () -> executeInCurrentThread(files));
    }
  }

  private void executeInCurrentThread(@NotNull Collection<VirtualFile> files, @Nullable ProgressIndicator indicator) {
    final Map<VirtualFile, Collection<VirtualFile>> sorted = HgUtil.sortByHgRoots(myProject, files);
    for (Map.Entry<VirtualFile, Collection<VirtualFile>> entry : sorted.entrySet()) {
      if (indicator != null) {
        if (indicator.isCanceled()) return;
        indicator.setFraction(0);
        indicator.setText2("Adding files to " + entry.getKey().getPresentableUrl());
      }
      addFilesSynchronously(entry.getKey(), entry.getValue(), indicator);
    }
  }

  private void addFilesSynchronously(VirtualFile repo, Collection<VirtualFile> files, @Nullable ProgressIndicator indicator) {
    final List<List<String>> chunks = VcsFileUtil.chunkFiles(repo, files);
    int currentChunk = 0;
    for (List<String> paths : chunks) {
      if (indicator != null) {
        if (indicator.isCanceled()) return;
        indicator.setFraction((double)currentChunk / chunks.size());
        currentChunk++;
      }
      new HgCommandExecutor(myProject).executeInCurrentThread(repo, "add", paths);
    }
  }

}
