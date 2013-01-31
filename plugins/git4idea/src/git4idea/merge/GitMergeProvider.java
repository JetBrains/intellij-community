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
package git4idea.merge;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider2;
import com.intellij.openapi.vcs.merge.MergeSession;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.util.GitFileUtils;
import git4idea.commands.GitSimpleHandler;
import git4idea.util.StringScanner;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Merge-changes provider for Git, used by IDEA internal 3-way merge tool
 */
public class GitMergeProvider implements MergeProvider2 {
  /**
   * the logger
   */
  private static final Logger log = Logger.getInstance(GitMergeProvider.class.getName());
  /**
   * The project instance
   */
  private final Project myProject;
  /**
   * If true the merge provider has a reverse meaning
   */
  private final boolean myReverse;
  /**
   * The revision that designates common parent for the files during the merge
   */
  private static final int ORIGINAL_REVISION_NUM = 1;
  /**
   * The revision that designates the file on the local branch
   */
  private static final int YOURS_REVISION_NUM = 2;
  /**
   * The revision that designates the remote file being merged
   */
  private static final int THEIRS_REVISION_NUM = 3;

  /**
   * A merge provider
   *
   * @param project a project for the provider
   */
  public GitMergeProvider(Project project) {
    this(project, false);
  }

  /**
   * A merge provider
   *
   * @param project a project for the provider
   * @param reverse if true, yours and theirs take a reverse meaning
   */
  public GitMergeProvider(Project project, boolean reverse) {
    myProject = project;
    myReverse = reverse;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  public MergeData loadRevisions(final VirtualFile file) throws VcsException {
    final MergeData mergeData = new MergeData();
    if (file == null) return mergeData;
    final VirtualFile root = GitUtil.getGitRoot(file);
    final FilePath path = VcsUtil.getFilePath(file.getPath());

    VcsRunnable runnable = new VcsRunnable() {
      @SuppressWarnings({"ConstantConditions"})
      public void run() throws VcsException {
        GitFileRevision original = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + ORIGINAL_REVISION_NUM));
        GitFileRevision current = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + yoursRevision()));
        GitFileRevision last = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + theirsRevision()));
        try {
          try {
            mergeData.ORIGINAL = original.getContent();
          }
          catch (Exception ex) {
            /// unable to load original revision, use the current instead
            /// This could happen in case if rebasing.
            mergeData.ORIGINAL = file.contentsToByteArray();
          }
          mergeData.CURRENT = loadRevisionCatchingErrors(current);
          mergeData.LAST = loadRevisionCatchingErrors(last);
          mergeData.LAST_REVISION_NUMBER = findLastRevisionNumber(root);
        }
        catch (IOException e) {
          throw new IllegalStateException("Failed to load file content", e);
        }
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, GitBundle.message("merge.load.files"), false, myProject);
    return mergeData;
  }

  @Nullable
  private VcsRevisionNumber findLastRevisionNumber(@NotNull VirtualFile root) {
    if (myReverse) {
      return resolveHead(root);
    }
    else {
      try {
        return GitRevisionNumber.resolve(myProject, root, "MERGE_HEAD");
      }
      catch (VcsException e) {
        log.info("Couldn't resolved the MERGE_HEAD in " + root, e); // this may be not a bug, just cherry-pick
        try {
          return GitRevisionNumber.resolve(myProject, root, "CHERRY_PICK_HEAD");
        }
        catch (VcsException e1) {
          log.info("Couldn't resolve neither MERGE_HEAD, nor the CHERRY_PICK_HEAD in " + root, e1);
          // for now, we don't know: maybe it is a conflicted file from rebase => then resolve the head.
          return resolveHead(root);
        }
      }
    }
  }

  @Nullable
  private GitRevisionNumber resolveHead(@NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(myProject, root, "HEAD");
    }
    catch (VcsException e) {
      log.error("Couldn't resolve the HEAD in " + root, e);
      return null;
    }
  }

  private byte[] loadRevisionCatchingErrors(final GitFileRevision revision) throws VcsException, IOException {
    try {
      return revision.getContent();
    } catch (VcsException e) {
      String m = e.getMessage().trim();
      if (m.startsWith("fatal: ambiguous argument ")
          || (m.startsWith("fatal: Path '") && m.contains("' exists on disk, but not in '"))
          || (m.contains("is in the index, but not at stage "))) {
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
      else {
        throw e;
      }
    }
  }

  /**
   * @return number for "yours" revision  (taking {@code revsere} flag in account)
   */
  private int yoursRevision() {
    return myReverse ? THEIRS_REVISION_NUM : YOURS_REVISION_NUM;
  }

  /**
   * @return number for "theirs" revision (taking {@code revsere} flag in account)
   */
  private int theirsRevision() {
    return myReverse ? YOURS_REVISION_NUM : THEIRS_REVISION_NUM;
  }

  /**
   * {@inheritDoc}
   */
  public void conflictResolvedForFile(VirtualFile file) {
    if (file == null) return;
    try {
      GitFileUtils.addFiles(myProject, GitUtil.getGitRoot(file), file);
    }
    catch (VcsException e) {
      log.error("Confirming conflict resolution failed", e);
    }
  }

  /**
   * {@inheritDoc}
   */
  public boolean isBinary(VirtualFile file) {
    return file.getFileType().isBinary();
  }

  @NotNull
  public MergeSession createMergeSession(List<VirtualFile> files) {
    return new MyMergeSession(files);
  }


  /**
   * The conflict descriptor
   */
  private static class Conflict {
    /**
     * the file in the conflict
     */
    VirtualFile myFile;
    /**
     * the root for the file
     */
    VirtualFile myRoot;
    /**
     * the status of theirs revision
     */
    Status myStatusTheirs;
    /**
     * the status
     */
    Status myStatusYours;

    /**
     * @return true if the merge operation can be applied
     */
    boolean isMergeable() {
      return true;
    }

    /**
     * The conflict status
     */
    enum Status {
      /**
       * the file was modified on the branch
       */
      MODIFIED,
      /**
       * the file was deleted on the branch
       */
      DELETED,
    }
  }


  /**
   * The merge session, it queries conflict information .
   */
  private class MyMergeSession implements MergeSession {
    /**
     * the map with conflicts
     */
    Map<VirtualFile, Conflict> myConflicts = new HashMap<VirtualFile, Conflict>();

    /**
     * A constructor from list of the files
     *
     * @param filesToMerge the files to process using merge dialog.
     */
    MyMergeSession(List<VirtualFile> filesToMerge) {
      // get conflict type by the file
      try {
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : GitUtil.sortFilesByGitRoot(filesToMerge).entrySet()) {
          Map<String, Conflict> cs = new HashMap<String, Conflict>();
          VirtualFile root = e.getKey();
          List<VirtualFile> files = e.getValue();
          GitSimpleHandler h = new GitSimpleHandler(myProject, root, GitCommand.LS_FILES);
          h.setNoSSH(true);
          h.setStdoutSuppressed(true);
          h.setSilent(true);
          h.addParameters("--exclude-standard", "--unmerged", "-t", "-z");
          h.endOptions();
          String output = h.run();
          StringScanner s = new StringScanner(output);
          while (s.hasMoreData()) {
            if (!"M".equals(s.spaceToken())) {
              s.boundedToken('\u0000');
              continue;
            }
            s.spaceToken(); // permissions
            s.spaceToken(); // commit hash
            int source = Integer.parseInt(s.tabToken());
            String file = s.boundedToken('\u0000');
            Conflict c = cs.get(file);
            if (c == null) {
              c = new Conflict();
              c.myRoot = root;
              cs.put(file, c);
            }
            if (source == theirsRevision()) {
              c.myStatusTheirs = Conflict.Status.MODIFIED;
            }
            else if (source == yoursRevision()) {
              c.myStatusYours = Conflict.Status.MODIFIED;
            }
            else if (source != ORIGINAL_REVISION_NUM) {
              throw new IllegalStateException("Unknown revision " + source + " for the file: " + file);
            }
          }
          for (VirtualFile f : files) {
            String path = VcsFileUtil.relativePath(root, f);
            Conflict c = cs.get(path);
            log.assertTrue(c != null, String.format("The conflict not found for the file: %s(%s)%nFull ls-files output: %n%s",
                                                    f.getPath(), path, output));
            c.myFile = f;
            if (c.myStatusTheirs == null) {
              c.myStatusTheirs = Conflict.Status.DELETED;
            }
            if (c.myStatusYours == null) {
              c.myStatusYours = Conflict.Status.DELETED;
            }
            myConflicts.put(f, c);
          }
        }
      }
      catch (VcsException ex) {
        throw new IllegalStateException("The git operation should not fail in this context", ex);
      }
    }

    public ColumnInfo[] getMergeInfoColumns() {
      return new ColumnInfo[]{new StatusColumn(false), new StatusColumn(true)};
    }

    public boolean canMerge(VirtualFile file) {
      Conflict c = myConflicts.get(file);
      return c != null;
    }

    public void conflictResolvedForFile(VirtualFile file, Resolution resolution) {
      Conflict c = myConflicts.get(file);
      assert c != null : "Conflict was not loaded for the file: " + file.getPath();
      try {
        Conflict.Status status;
        switch (resolution) {
          case AcceptedTheirs:
            status = c.myStatusTheirs;
            break;
          case AcceptedYours:
            status = c.myStatusYours;
            break;
          case Merged:
            status = Conflict.Status.MODIFIED;
            break;
          default:
            throw new IllegalArgumentException("Unsupported resolution for unmergable files(" + file.getPath() + "): " + resolution);
        }
        switch (status) {
          case MODIFIED:
            GitFileUtils.addFiles(myProject, c.myRoot, file);
            break;
          case DELETED:
            GitFileUtils.deleteFiles(myProject, c.myRoot, file);
            break;
          default:
            throw new IllegalArgumentException("Unsupported status(" + file.getPath() + "): " + status);
        }
      }
      catch (VcsException e) {
        log.error("Unexpected exception during the git operation (" + file.getPath() + ")", e);
      }
    }

    /**
     * The status column, the column shows either "yours" or "theirs" status
     */
    class StatusColumn extends ColumnInfo<VirtualFile, String> {
      /**
       * if false, "yours" status is displayed, otherwise "theirs"
       */
      private final boolean myIsTheirs;

      /**
       * The constructor
       *
       * @param isTheirs if true columns represents status in 'theirs' revision, if false in 'ours'
       */
      public StatusColumn(boolean isTheirs) {
        super(isTheirs ? GitBundle.message("merge.tool.column.theirs.status") : GitBundle.message("merge.tool.column.yours.status"));
        myIsTheirs = isTheirs;
      }

      /**
       * {@inheritDoc}
       */
      public String valueOf(VirtualFile file) {
        Conflict c = myConflicts.get(file);
        assert c != null : "No conflict for the file " + file;
        Conflict.Status s = myIsTheirs ? c.myStatusTheirs : c.myStatusYours;
        switch (s) {
          case MODIFIED:
            return GitBundle.message("merge.tool.column.status.modified");
          case DELETED:
            return GitBundle.message("merge.tool.column.status.deleted");
          default:
            throw new IllegalStateException("Unknown status " + s + " for file " + file.getPath());
        }
      }

      @Override
      public String getMaxStringValue() {
        return GitBundle.message("merge.tool.column.status.modified");
      }

      @Override
      public int getAdditionalWidth() {
        return 10;
      }
    }
  }
}
