/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.checkin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitHandler;
import git4idea.commands.GitLineHandler;
import git4idea.commands.StringScanner;
import git4idea.config.GitVcsSettings;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.rebase.GitRebaseEditorService;
import git4idea.update.GitBaseRebaseProcess;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This is subclass of {@link git4idea.update.GitBaseRebaseProcess} that implement rebase operation for {@link GitPushActiveBranchesDialog}.
 * This operation reorders commits if needed.
 */
public class GitPushRebaseProcess extends GitBaseRebaseProcess {
  /**
   * The logger
   */
  private static final Logger LOG = Logger.getInstance(GitPushRebaseProcess.class.getName());
  /**
   * Save changes policy
   */
  private final GitVcsSettings.UpdateChangesPolicy mySavePolicy;
  /**
   * The map from vcs root to list of the commit identifier for reordered commits, if vcs root is not provided, the reordering is not needed.
   */
  private final Map<VirtualFile, List<String>> myReorderedCommits;
  /**
   * A set of roots that have non-pushed merges
   */
  private final Set<VirtualFile> myRootsWithMerges;
  /**
   * The registration number for the rebase editor
   */
  private Integer myRebaseEditorNo;
  /**
   * The rebase editor service
   */
  private final GitRebaseEditorService myRebaseEditorService;

  /**
   * The constructor
   *
   * @param vcs             the vcs instance
   * @param project         the project instance
   * @param exceptions      the list of exceptions for the process
   * @param savePolicy      the save policy for the rebase process
   * @param rootsWithMerges a set of roots with merges
   */
  public GitPushRebaseProcess(final GitVcs vcs,
                              final Project project,
                              List<VcsException> exceptions,
                              GitVcsSettings.UpdateChangesPolicy savePolicy,
                              Map<VirtualFile, List<String>> reorderedCommits,
                              Set<VirtualFile> rootsWithMerges) {
    super(vcs, project, exceptions);
    mySavePolicy = savePolicy;
    myReorderedCommits = reorderedCommits;
    myRootsWithMerges = rootsWithMerges;
    myRebaseEditorService = GitRebaseEditorService.getInstance();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected GitLineHandler makeStartHandler(VirtualFile root) throws VcsException {
    List<String> commits = myReorderedCommits.get(root);
    boolean hasMerges = myRootsWithMerges.contains(root);
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.REBASE);
    if (commits != null || hasMerges) {
      h.addParameters("-i");
      PushRebaseEditor pushRebaseEditor = new PushRebaseEditor(root, commits, hasMerges, h);
      myRebaseEditorNo = pushRebaseEditor.getHandlerNo();
      myRebaseEditorService.configureHandler(h, myRebaseEditorNo);
      if (hasMerges) {
        h.addParameters("-p");
      }
    }
    h.addParameters("-m", "-v");
    GitBranch currentBranch = GitBranch.current(myProject, root);
    assert currentBranch != null;
    GitBranch trackedBranch = currentBranch.tracked(myProject, root);
    assert trackedBranch != null;
    h.addParameters(trackedBranch.getFullName());
    return h;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void cleanupHandler(VirtualFile root, GitLineHandler h) {
    if (myRebaseEditorNo != null) {
      myRebaseEditorService.unregisterHandler(myRebaseEditorNo);
      myRebaseEditorNo = null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void configureRebaseEditor(VirtualFile root, GitLineHandler h) {
    GitInteractiveRebaseEditorHandler editorHandler = new GitInteractiveRebaseEditorHandler(myRebaseEditorService, myProject, root, h);
    editorHandler.setRebaseEditorShown();
    myRebaseEditorNo = editorHandler.getHandlerNo();
    myRebaseEditorService.configureHandler(h, myRebaseEditorNo);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String makeStashMessage() {
    return "Uncommitted changes before rebase operation in push dialog at " +
           DateFormatUtil.formatDateTime(Clock.getTime());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected GitVcsSettings.UpdateChangesPolicy getUpdatePolicy() {
    return mySavePolicy;
  }

  /**
   * The rebase editor that just overrides the list of commits
   */
  class PushRebaseEditor extends GitInteractiveRebaseEditorHandler {
    /**
     * The reordered commits
     */
    private final List<String> myCommits;
    /**
     * The true means that the root has merges
     */
    private final boolean myHasMerges;

    /**
     * The constructor from fields that is expected to be
     * accessed only from {@link git4idea.rebase.GitRebaseEditorService}.
     *
     * @param root      the git repository root
     * @param commits   the reordered commits
     * @param hasMerges if true, the vcs root has merges
     */
    public PushRebaseEditor(final VirtualFile root, List<String> commits, boolean hasMerges, GitHandler h) {
      super(myRebaseEditorService, myProject, root, h);
      myCommits = commits;
      myHasMerges = hasMerges;
    }

    /**
     * {@inheritDoc}
     */
    public int editCommits(String path) {
      if (!myRebaseEditorShown) {
        myRebaseEditorShown = true;
        if (myHasMerges) {
          return 0;
        }
        try {
          TreeMap<String, String> pickLines = new TreeMap<String, String>();
          StringScanner s = new StringScanner(new String(FileUtil.loadFileText(new File(path), GitUtil.UTF8_ENCODING)));
          while (s.hasMoreData()) {
            if (!s.tryConsume("pick ")) {
              s.line();
              continue;
            }
            String commit = s.spaceToken();
            pickLines.put(commit, "pick " + commit + " " + s.line());
          }
          PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(path), GitUtil.UTF8_ENCODING));
          try {
            for (String commit : myCommits) {
              String key = pickLines.headMap(commit + "\u0000").lastKey();
              if (key == null || !commit.startsWith(key)) {
                continue; // commit from merged branch
              }
              w.print(pickLines.get(key) + "\n");
            }
          }
          finally {
            w.close();
          }
          return 0;
        }
        catch (Exception ex) {
          LOG.error("Editor failed: ", ex);
          return 1;
        }
      }
      else {
        return super.editCommits(path);
      }
    }
  }
}
