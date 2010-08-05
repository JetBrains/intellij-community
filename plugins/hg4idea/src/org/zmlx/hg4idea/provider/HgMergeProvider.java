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
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgUtil;
import org.zmlx.hg4idea.command.HgResolveCommand;

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
        final HgResolveCommand.MergeData resolveData = new HgResolveCommand(myProject).getResolveData(HgUtil.getHgRootOrThrow(myProject, file), file);
        mergeData.ORIGINAL = resolveData.getBase();
        mergeData.CURRENT = resolveData.getLocal();
        mergeData.LAST = resolveData.getOther();
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
