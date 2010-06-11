/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsVFSListener;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import org.zmlx.hg4idea.command.HgAddCommand;
import org.zmlx.hg4idea.command.HgCopyCommand;
import org.zmlx.hg4idea.command.HgMoveCommand;
import org.zmlx.hg4idea.command.HgRemoveCommand;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Listens to VFS events (such as adding or deleting bunch of files) and performs necessary operations with the VCS.
 * @author Kirill Likhodedov
 */
public class HgVFSListener extends VcsVFSListener {

  private final VcsDirtyScopeManager dirtyScopeManager; 

  protected HgVFSListener(final Project project, final HgVcs vcs) {
    super(project, vcs);
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  @Override
  protected String getAddTitle() {
    return HgVcsMessages.message("hg4idea.add.title");
  }

  @Override
  protected String getSingleFileAddTitle() {
    return HgVcsMessages.message("hg4idea.add.single.title");
  }

  @Override
  protected String getSingleFileAddPromptTemplate() {
    return HgVcsMessages.message("hg4idea.add.body");
  }

  @Override
  protected void performAdding(Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
    (new VcsBackgroundTask<VirtualFile>(myProject,
                                        HgVcsMessages.message("hg4idea.add.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        addedFiles) {
      protected void process(final VirtualFile file) throws VcsException {
        if (file.isDirectory()) {
          return;
        }
        final VirtualFile copyFrom = copyFromMap.get(file);
        if (copyFrom != null) {
          (new HgCopyCommand(myProject)).execute(new HgFile(myProject, copyFrom), new HgFile(myProject, file));
        } else {
          (new HgAddCommand(myProject)).execute(new HgFile(myProject, file));
        }
        dirtyScopeManager.fileDirty(file);
      }

    }).queue();
  }

  @Override
  protected String getDeleteTitle() {
    return HgVcsMessages.message("hg4idea.remove.multiple.title");
  }

  @Override
  protected String getSingleFileDeleteTitle() {
    return HgVcsMessages.message("hg4idea.remove.single.title");
  }

  @Override
  protected String getSingleFileDeletePromptTemplate() {
    return HgVcsMessages.message("hg4idea.remove.single.body");
  }

  @Override
  protected void performDeletion(List<FilePath> filesToDelete) {
    (new VcsBackgroundTask<FilePath>(myProject,
                                        HgVcsMessages.message("hg4idea.remove.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        filesToDelete) {
      protected void process(final FilePath file) throws VcsException {
        if (file.isDirectory()) {
          return;
        }
        (new HgRemoveCommand(myProject)).execute(new HgFile(VcsUtil.getVcsRootFor(myProject, file), file));
        dirtyScopeManager.fileDirty(file);
      }

    }).queue();
  }

  @Override
  protected void performMoveRename(List<MovedFileInfo> movedFiles) {
    (new VcsBackgroundTask<MovedFileInfo>(myProject,
                                        HgVcsMessages.message("hg4idea.move.progress"),
                                        VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
                                        movedFiles) {
      protected void process(final MovedFileInfo file) throws VcsException {
        final FilePath source = VcsUtil.getFilePath(file.myOldPath);
        final FilePath target = VcsUtil.getFilePath(file.myNewPath);
        (new HgMoveCommand(myProject)).execute(new HgFile(VcsUtil.getVcsRootFor(myProject, source), source), new HgFile(VcsUtil.getVcsRootFor(myProject, target), target));
        dirtyScopeManager.fileDirty(source);
        dirtyScopeManager.fileDirty(target);
      }

    }).queue();
  }

  @Override
  protected boolean isDirectoryVersioningSupported() {
    return false;
  }
}
