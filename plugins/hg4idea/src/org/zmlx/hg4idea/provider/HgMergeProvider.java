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
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgContentRevision;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;

import java.io.File;
import java.io.IOException;

/**
 * @author Kirill Likhodedov
 */
public class HgMergeProvider implements MergeProvider {
  private static final Logger LOG = Logger.getInstance(HgMergeProvider.class.getName());
  private final Project myProject;

  public HgMergeProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public MergeData loadRevisions(final VirtualFile file) throws VcsException {
    final MergeData mergeData = new MergeData();
    final VcsRunnable runnable = new VcsRunnable() {
      public void run() throws VcsException {
        final HgWorkingCopyRevisionsCommand command = new HgWorkingCopyRevisionsCommand(myProject);
        final VirtualFile repo = HgUtil.getHgRootOrThrow(myProject, file);
        final HgFile hgFile = new HgFile(myProject, file);

        HgRevisionNumber serverRevisionNumber, baseRevisionNumber;
        // there are two possibilities: we have checked in local changes in the selected file or we didn't.
        if (wasFileCheckedIn(repo, file)) {
          // 1. We checked in.
          // We have a merge in progress, which means we have 2 heads (parents).
          // the latest one is "their" revision pulled from the parent repo,
          // the earlier parent is the local change.
          // to retrieve the base version we get the parent of the local change, i.e. the [only] parent of the second parent.
          final Pair<HgRevisionNumber, HgRevisionNumber> parents = command.parents(repo, file);
          serverRevisionNumber = parents.first;
          final HgContentRevision local = new HgContentRevision(myProject, hgFile, parents.second);
          mergeData.CURRENT = local.getContentAsBytes();
          // we are sure that we have a grandparent, because otherwise we'll get "repository is unrelated" error while pulling,
          // due to different root changesets which is prohibited.
          baseRevisionNumber = command.parents(repo, file, parents.second).first;
        } else {
          // 2. local changes are not checked in.
          // then there is only one parent, which is server changes.
          // local changes are retrieved from the file system, they are not in the Mercurial yet.
          // base is the only parent of server changes.
          serverRevisionNumber = command.parents(repo, file).first;
          baseRevisionNumber = command.parents(repo, file, serverRevisionNumber).first;
          final File origFile = new File(file.getPath() + ".orig");
          try {
            mergeData.CURRENT = VcsUtil.getFileByteContent(origFile);
          } catch (IOException e) {
            LOG.info("Couldn't retrieve byte content of the file: " + origFile.getPath(), e);
          }
        }

        if (baseRevisionNumber != null) {
          final HgContentRevision base = new HgContentRevision(myProject, hgFile, baseRevisionNumber);
          mergeData.ORIGINAL = base.getContentAsBytes();
        } else { // no base revision means that the file was added simultaneously with different content in both repositories
          mergeData.ORIGINAL = new byte[0];
        }
        final HgContentRevision server = new HgContentRevision(myProject, hgFile, serverRevisionNumber);
        mergeData.LAST = server.getContentAsBytes();
        file.refresh(false, false);
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, VcsBundle.message("multiple.file.merge.loading.progress.title"), false, myProject);
    return mergeData;
  }

  @Override
  public void conflictResolvedForFile(VirtualFile file) {
    try {
      new HgResolveCommand(myProject).markResolved(HgUtil.getHgRootOrThrow(myProject, file), file);
    } catch (VcsException e) {
      LOG.error("Couldn't mark file resolved, because it is not under Mercurial root.");
    }
  }

  @Override
  public boolean isBinary(VirtualFile file) {
    return file.getFileType().isBinary();
  }

  /**
   * Checks if the given file was checked in before the merge start.
   * @param repo repository to work on.
   * @param file file to be checked.
   * @return True if the file was checked in before merge, false if it wasn't.
   */
  private boolean wasFileCheckedIn(VirtualFile repo, VirtualFile file) {
    // in the case of merge if the file was checked in, it will have 2 parents after hg pull. If it wasn't, it would have only one parent
    final Pair<HgRevisionNumber, HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(repo, file);
    return parents.second != null;
  }

}
