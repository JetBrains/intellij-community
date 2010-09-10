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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgResolveStatusEnum;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HgConflictResolver {

  @NotNull private final Project myProject;
  private final UpdatedFiles updatedFiles;

  public HgConflictResolver(@NotNull Project project) {
    this(project, null);
  }

  public HgConflictResolver(Project project, UpdatedFiles updatedFiles) {
    this.myProject = project;
    this.updatedFiles = updatedFiles;
  }

  public void resolve(final VirtualFile repo) {
    Map<HgFile, HgResolveStatusEnum> resolves = new HgResolveCommand(myProject).list(repo);
    final List<VirtualFile> conflicts = new ArrayList<VirtualFile>();
    for (Map.Entry<HgFile, HgResolveStatusEnum> entry : resolves.entrySet()) {
      File file = entry.getKey().getFile();
      String fileGroupId = null;
      switch (entry.getValue()) {
        case UNRESOLVED:
          conflicts.add(VcsUtil.getVirtualFile(file));
          fileGroupId = FileGroup.MERGED_WITH_CONFLICT_ID;
          break;
        case RESOLVED:
          fileGroupId = FileGroup.MERGED_ID;
          break;
        default:
      }
      if (updatedFiles != null && fileGroupId != null) {
        updatedFiles.getGroupById(FileGroup.UPDATED_ID).remove(file.getAbsolutePath());
        //TODO get the correct revision to pass to the UpdatedFiles
        updatedFiles.getGroupById(fileGroupId)
          .add(file.getPath(), HgVcs.VCS_NAME, null); 
      }
    }

    if (conflicts.isEmpty()) {
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      AbstractVcsHelper.getInstance(myProject).showMergeDialog(conflicts, HgVcs.getInstance(myProject).getMergeProvider());
    }
  }

}
