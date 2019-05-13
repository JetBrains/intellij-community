/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgContentRevision;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgResolveCommand;
import org.zmlx.hg4idea.command.HgWorkingCopyRevisionsCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgPromptCommandExecutor;
import org.zmlx.hg4idea.util.HgUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
  public MergeData loadRevisions(@NotNull final VirtualFile file) throws VcsException {
    final MergeData mergeData = new MergeData();
    final VcsRunnable runnable = new VcsRunnable() {
      @Override
      public void run() throws VcsException {
        final HgWorkingCopyRevisionsCommand command = new HgWorkingCopyRevisionsCommand(myProject);
        final VirtualFile repo = HgUtil.getHgRootOrThrow(myProject, file);
        final HgFile hgFile = new HgFile(myProject, file);

        HgRevisionNumber serverRevisionNumber;
        HgRevisionNumber localRevisionNumber;
        HgRevisionNumber baseRevisionNumber = null;
        // there are two possibilities: we have checked in local changes in the selected file or we didn't.
        if (wasFileCheckedIn(repo, file)) {
          // 1. We checked in.
          // We have a merge in progress, which means we have 2 heads (parents).
          // the second one is "their" revision pulled from the parent repo,
          // first parent is the local change.
          // to retrieve the base version we get the parent of the local change, i.e. the [only] parent of the first parent.
          //Which one is local revision depends on which one is merged with,
          // i.e if you update to 17 revision and then merge it with 23, so 17 is your local and 17->parent is your base revision.
          // This may produce misunderstanding when you update your project with merging (your update firstly to next revisions  and then
          // merge with previous). see http://hgbook.red-bean.com/read/managing-releases-and-branchy-development.html
          final Couple<HgRevisionNumber> parents = command.parents(repo, file);
          serverRevisionNumber = parents.second;
          localRevisionNumber = parents.first;
          final HgContentRevision local = HgContentRevision.create(myProject, hgFile, localRevisionNumber);
          mergeData.CURRENT = local.getContentAsBytes();
          // we are sure that we have a common ancestor, because otherwise we'll get "repository is unrelated" error while pulling,
          // due to different root changesets which is prohibited.
          // Find common ancestor of two revisions : hg debugancestor rev1 rev2
          // Using quotes may produce wrong escaping errors on Unix-type systems
          List<String> arguments = new ArrayList<>();
          String localChangeset = localRevisionNumber.getChangeset();
          String serverChangeset = serverRevisionNumber.getChangeset();
          arguments.add(StringUtil.isEmptyOrSpaces(localChangeset) ? localRevisionNumber.getRevision() : localChangeset);
          arguments.add(StringUtil.isEmptyOrSpaces(serverChangeset) ? serverRevisionNumber.getRevision() : serverChangeset);
          HgCommandResult result = new HgPromptCommandExecutor(myProject).executeInCurrentThread(repo, "debugancestor", arguments);
          if (result != null) {
            String output = result.getRawOutput();
            final List<String> parts = StringUtil.split(output, ":");
            if (parts.size() < 2) {
              LOG.info("Couldn't parse result of debugancestor command execution " + arguments);
              new HgCommandResultNotifier(myProject)
                .notifyError(null, HgVcsMessages.message("hg4idea.error.debugancestor.command.execution"),
                             HgVcsMessages.message("hg4idea.error.debugancestor.command.description"));
            }
            else {
              baseRevisionNumber = HgRevisionNumber.getInstance(parts.get(0), parts.get(1));
            }
          }
          else {
            LOG.info(HgVcsMessages.message("hg4idea.error.debugancestor.command.execution") + arguments);
            new HgCommandResultNotifier(myProject)
              .notifyError(null, HgVcsMessages.message("hg4idea.error.debugancestor.command.execution"),
                           HgVcsMessages.message("hg4idea.error.debugancestor.command.description"));
          }
        }
        else {
          // 2. local changes are not checked in.
          // then there is only one parent, which is server changes.
          // local changes are retrieved from the file system, they are not in the Mercurial yet.
          // base is the only parent of server changes.
          serverRevisionNumber = command.parents(repo, file).first;
          baseRevisionNumber = command.parents(repo, file, serverRevisionNumber).first;
          final File origFile = new File(file.getPath() + ".orig");
          mergeData.CURRENT = VcsUtil.getFileByteContent(origFile);
        }

        if (baseRevisionNumber != null) {
          final HgContentRevision base = HgContentRevision.create(myProject, hgFile, baseRevisionNumber);
          //if file doesn't exist in ancestor revision the base revision should be empty
          mergeData.ORIGINAL = base.getContent() != null ? base.getContentAsBytes() : ArrayUtil.EMPTY_BYTE_ARRAY;
        }
        else { // no base revision means that the file was added simultaneously with different content in both repositories
          mergeData.ORIGINAL = ArrayUtil.EMPTY_BYTE_ARRAY;
        }
        final HgContentRevision server = HgContentRevision.create(myProject, hgFile, serverRevisionNumber);
        mergeData.LAST = server.getContentAsBytes();
        file.refresh(false, false);
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, VcsBundle.message("multiple.file.merge.loading.progress.title"), false, myProject);
    return mergeData;
  }

  @Override
  public void conflictResolvedForFile(@NotNull VirtualFile file) {
    try {
      new HgResolveCommand(myProject).markResolved(HgUtil.getHgRootOrThrow(myProject, file), file);
    } catch (VcsException e) {
      LOG.error("Couldn't mark file resolved, because it is not under Mercurial root.");
    }
  }

  @Override
  public boolean isBinary(@NotNull VirtualFile file) {
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
    final Couple<HgRevisionNumber> parents = new HgWorkingCopyRevisionsCommand(myProject).parents(repo, file);
    return parents.second != null;
  }

}
