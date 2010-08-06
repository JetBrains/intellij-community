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
        // we have a merge in progress, which means we have 2 heads (parents).
        // the latest one is "their" revision pulled from the parent repo,
        // the earlier parent is the local change.
        // to retrieve the base version we get the parent of the local change, i.e. the [only] parent of the second parent.
        final HgWorkingCopyRevisionsCommand command = new HgWorkingCopyRevisionsCommand(myProject);
        final Pair<HgRevisionNumber, HgRevisionNumber> parents = command.parents(HgUtil.getHgRootOrThrow(myProject, file), file);
        // we are sure that we have a grandparent, because otherwise we'll get "repository is unrelated" error while pulling,
        // due to different root changesets which is prohibited.
        final HgRevisionNumber grandParent = command.parents(HgUtil.getHgRootOrThrow(myProject, file), file, parents.second).first;

        final HgFile hgFile = new HgFile(myProject, file);
        final HgContentRevision server = new HgContentRevision(myProject, hgFile, parents.first);
        final HgContentRevision local  = new HgContentRevision(myProject, hgFile, parents.second);
        final HgContentRevision base = new HgContentRevision(myProject, hgFile, grandParent);

        mergeData.ORIGINAL = base.getContentAsBytes();
        mergeData.CURRENT = local.getContentAsBytes();
        mergeData.LAST = server.getContentAsBytes();
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

}
