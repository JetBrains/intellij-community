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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgResolveStatusEnum;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.dvcs.DvcsUtil.findVirtualFilesWithRefresh;
import static com.intellij.dvcs.DvcsUtil.sortVirtualFilesByPresentation;

public final class HgConflictResolver {

  @NotNull private final Project myProject;
  private final UpdatedFiles updatedFiles;

  public HgConflictResolver(@NotNull Project project) {
    this(project, null);
  }

  public HgConflictResolver(@NotNull Project project, UpdatedFiles updatedFiles) {
    this.myProject = project;
    this.updatedFiles = updatedFiles;
  }

  public void resolve(final VirtualFile repo) {
    final Map<HgFile, HgResolveStatusEnum> resolves = new HgResolveCommand(myProject).getListSynchronously(repo);
    final List<File> conflictFiles = new ArrayList<>();

    for (HgFile hgFile : resolves.keySet()) {
      File file = hgFile.getFile();
      if (resolves.get(hgFile) == HgResolveStatusEnum.UNRESOLVED) {
        conflictFiles.add(file);
        updateUpdatedFiles(file, true);
      }
      else {
        updateUpdatedFiles(file, false);
      }
    }
    if (conflictFiles.isEmpty()) return;

    final HgVcs vcs = HgVcs.getInstance(myProject);
    if (vcs == null) return;
    final List<VirtualFile> conflicts = sortVirtualFilesByPresentation(findVirtualFilesWithRefresh(conflictFiles));
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        AbstractVcsHelper.getInstance(myProject).showMergeDialog(conflicts, vcs.getMergeProvider());
      }
    }, ModalityState.defaultModalityState());
  }

  private void updateUpdatedFiles(@NotNull File file, boolean unresolved) {
    if (updatedFiles != null) {
      updatedFiles.getGroupById(FileGroup.UPDATED_ID).remove(file.getAbsolutePath());
      //TODO get the correct revision to pass to the UpdatedFiles
      updatedFiles.getGroupById(unresolved ? FileGroup.MERGED_WITH_CONFLICT_ID : FileGroup.MERGED_ID)
        .add(file.getPath(), HgVcs.VCS_NAME, null);
    }
  }

  public static boolean hasConflicts(final Project project, VirtualFile repo) {
    Map<HgFile, HgResolveStatusEnum> resolves = new HgResolveCommand(project).getListSynchronously(repo);
    return ContainerUtil.exists(resolves.values(), new Condition<HgResolveStatusEnum>() {
      @Override
      public boolean value(HgResolveStatusEnum resolveStatus) {
        return resolveStatus == HgResolveStatusEnum.UNRESOLVED;
      }
    });
  }
}
