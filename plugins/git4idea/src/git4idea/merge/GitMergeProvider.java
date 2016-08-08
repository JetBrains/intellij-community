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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.merge.MergeProvider2;
import com.intellij.openapi.vcs.merge.MergeSession;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsRunnable;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitLineHandlerAdapter;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitFileUtils;
import git4idea.util.StringScanner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static git4idea.GitUtil.CHERRY_PICK_HEAD;
import static git4idea.GitUtil.MERGE_HEAD;

/**
 * Merge-changes provider for Git, used by IDEA internal 3-way merge tool
 */
public class GitMergeProvider implements MergeProvider2 {
  private static final int ORIGINAL_REVISION_NUM = 1; // common parent
  private static final int YOURS_REVISION_NUM = 2; // file content on the local branch: "Yours"
  private static final int THEIRS_REVISION_NUM = 3; // remote file content: "Theirs"

  private static final Logger LOG = Logger.getInstance(GitMergeProvider.class);

  @NotNull private final Project myProject;
  /**
   * If true the merge provider has a reverse meaning, i. e. yours and theirs are swapped.
   * It should be used when conflict is resolved after rebase or unstash.
   */
  @NotNull private final Set<VirtualFile> myReverseRoots;

  private enum ReverseRequest {
    REVERSE,
    FORWARD,
    DETECT
  }

  private GitMergeProvider(@NotNull Project project, @NotNull Set<VirtualFile> reverseRoots) {
    myProject = project;
    myReverseRoots = reverseRoots;
  }

  public GitMergeProvider(@NotNull Project project, boolean reverse) {
    this(project, findReverseRoots(project, reverse ? ReverseRequest.REVERSE : ReverseRequest.FORWARD));
  }

  @NotNull
  public static MergeProvider detect(@NotNull Project project) {
    return new GitMergeProvider(project, findReverseRoots(project, ReverseRequest.DETECT));
  }

  @NotNull
  private static Set<VirtualFile> findReverseRoots(@NotNull Project project, @NotNull ReverseRequest reverseOrDetect) {
    Set<VirtualFile> reverseMap = ContainerUtil.newHashSet();
    for (GitRepository repository : GitUtil.getRepositoryManager(project).getRepositories()) {
      boolean reverse;
      if (reverseOrDetect == ReverseRequest.DETECT) {
        reverse = repository.getState().equals(GitRepository.State.REBASING);
      }
      else {
        reverse = reverseOrDetect == ReverseRequest.REVERSE;
      }
      if (reverse) {
        reverseMap.add(repository.getRoot());
      }
    }
    return reverseMap;
  }

  @Override
  @NotNull
  public MergeData loadRevisions(@NotNull final VirtualFile file) throws VcsException {
    final MergeData mergeData = new MergeData();
    final VirtualFile root = GitUtil.getGitRoot(file);
    final FilePath path = VcsUtil.getFilePath(file.getPath());

    VcsRunnable runnable = new VcsRunnable() {
      @Override
      @SuppressWarnings({"ConstantConditions"})
      public void run() throws VcsException {
        GitFileRevision original = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + ORIGINAL_REVISION_NUM));
        GitFileRevision current = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + yoursRevision(root)));
        GitFileRevision last = new GitFileRevision(myProject, path, new GitRevisionNumber(":" + theirsRevision(root)));
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

          // TODO: can be done once for a root
          mergeData.CURRENT_REVISION_NUMBER = findCurrentRevisionNumber(root);
          mergeData.LAST_REVISION_NUMBER = findLastRevisionNumber(root);
          mergeData.ORIGINAL_REVISION_NUMBER = findOriginalRevisionNumber(root, mergeData.CURRENT_REVISION_NUMBER, mergeData.LAST_REVISION_NUMBER);


          Trinity<String, String, String> blobs = getAffectedBlobs(root, file);

          mergeData.CURRENT_FILE_PATH = getBlobPathInRevision(root, file, blobs.getFirst(), mergeData.CURRENT_REVISION_NUMBER);
          mergeData.ORIGINAL_FILE_PATH = getBlobPathInRevision(root, file, blobs.getSecond(), mergeData.ORIGINAL_REVISION_NUMBER);
          mergeData.LAST_FILE_PATH = getBlobPathInRevision(root, file, blobs.getThird(), mergeData.LAST_REVISION_NUMBER);
        }
        catch (IOException e) {
          throw new IllegalStateException("Failed to load file content", e);
        }
      }
    };
    VcsUtil.runVcsProcessWithProgress(runnable, GitBundle.message("merge.load.files"), false, myProject);
    return mergeData;
  }

  @NotNull
  private Trinity<String, String, String> getAffectedBlobs(@NotNull VirtualFile root, @NotNull VirtualFile file) {
    try {
      GitSimpleHandler h = new GitSimpleHandler(myProject, root, GitCommand.LS_FILES);
      h.addParameters("--exclude-standard", "--unmerged", "-z");
      h.endOptions();
      h.addRelativeFiles(Collections.singleton(file));

      String output = h.run();
      StringScanner s = new StringScanner(output);

      String lastBlob = null;
      String currentBlob = null;
      String originalBlob = null;

      while (s.hasMoreData()) {
        s.spaceToken(); // permissions
        String blob = s.spaceToken();
        int source = Integer.parseInt(s.tabToken()); // stage
        s.boundedToken('\u0000'); // file name

        if (source == theirsRevision(root)) {
          lastBlob = blob;
        }
        else if (source == yoursRevision(root)) {
          currentBlob = blob;
        }
        else if (source == ORIGINAL_REVISION_NUM) {
          originalBlob = blob;
        }
        else {
          throw new IllegalStateException("Unknown revision " + source + " for the file: " + file);
        }
      }
      return Trinity.create(currentBlob, originalBlob, lastBlob);
    }
    catch (VcsException e) {
      LOG.warn(e);
      return Trinity.create(null, null, null);
    }
  }

  @Nullable
  private FilePath getBlobPathInRevision(@NotNull VirtualFile root,
                                         @NotNull VirtualFile file,
                                         @Nullable String blob,
                                         @Nullable VcsRevisionNumber revision) {
    if (blob == null || revision == null) return null;

    // fast check if file was not renamed
    FilePath path = doGetBlobPathInRevision(root, blob, revision, file);
    if (path != null) return path;

    return doGetBlobPathInRevision(root, blob, revision, null);
  }

  @Nullable
  private FilePath doGetBlobPathInRevision(@NotNull final VirtualFile root,
                                           @NotNull final String blob,
                                           @NotNull VcsRevisionNumber revision,
                                           @Nullable VirtualFile file) {
    final FilePath[] result = new FilePath[1];
    final boolean[] pathAmbiguous = new boolean[1];

    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.LS_TREE);
    h.addParameters(revision.asString());

    if (file != null) {
      h.endOptions();
      h.addRelativeFiles(Collections.singleton(file));
    }
    else {
      h.addParameters("-r");
      h.endOptions();
    }

    h.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) return;
        if (!line.contains(blob)) return;
        if (pathAmbiguous[0]) return;

        try {
          StringScanner s = new StringScanner(line);
          s.spaceToken(); // permissions
          String type = s.spaceToken(); // type
          String recordBlob = s.tabToken(); // blob
          FilePath file = VcsUtil.getFilePath(root, GitUtil.unescapePath(s.line()));

          if (!"blob".equals(type)) return;
          if (!blob.equals(recordBlob)) return;

          if (result[0] == null) {
            result[0] = file;
          }
          else {
            // there are multiple files with given content in this revision.
            // we don't know which is right, so do not return any
            pathAmbiguous[0] = true;
          }
        }
        catch (VcsException e) {
          LOG.warn(e);
        }
      }
    });
    h.runInCurrentThread(null);

    if (pathAmbiguous[0]) return null;
    return result[0];
  }

  @Nullable
  private GitRevisionNumber findLastRevisionNumber(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? resolveHead(root) : resolveMergeHead(root);
  }

  @Nullable
  private GitRevisionNumber findCurrentRevisionNumber(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? resolveMergeHead(root) : resolveHead(root);
  }

  @Nullable
  private GitRevisionNumber findOriginalRevisionNumber(@NotNull VirtualFile root,
                                                       @Nullable VcsRevisionNumber currentRevision,
                                                       @Nullable VcsRevisionNumber lastRevision) {
    if (currentRevision == null || lastRevision == null) return null;
    try {
      return GitHistoryUtils.getMergeBase(myProject, root, currentRevision.asString(), lastRevision.asString());
    }
    catch (VcsException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Nullable
  private GitRevisionNumber resolveMergeHead(@NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(myProject, root, MERGE_HEAD);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve the MERGE_HEAD in " + root, e); // this may be not a bug, just cherry-pick
    }

    try {
      return GitRevisionNumber.resolve(myProject, root, CHERRY_PICK_HEAD);
    }
    catch (VcsException e) {
      LOG.info("Couldn't resolve the CHERRY_PICK_HEAD in " + root, e);
    }

    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(root);
    assert repository != null;

    File rebaseApply = repository.getRepositoryFiles().getRebaseApplyDir();
    GitRevisionNumber rebaseRevision = readRevisionFromFile(new File(rebaseApply, "original-commit"));
    if (rebaseRevision != null) return rebaseRevision;

    File rebaseMerge = repository.getRepositoryFiles().getRebaseMergeDir();
    GitRevisionNumber mergeRevision = readRevisionFromFile(new File(rebaseMerge, "stopped-sha"));
    if (mergeRevision != null) return mergeRevision;

    return null;
  }

  @Nullable
  private static GitRevisionNumber readRevisionFromFile(@NotNull File file) {
    if (!file.exists()) return null;
    String revision = DvcsUtil.tryLoadFileOrReturn(file, null, CharsetToolkit.UTF8);
    if (revision == null) return null;
    return new GitRevisionNumber(revision);
  }

  @Nullable
  private GitRevisionNumber resolveHead(@NotNull VirtualFile root) {
    try {
      return GitRevisionNumber.resolve(myProject, root, "HEAD");
    }
    catch (VcsException e) {
      LOG.error("Couldn't resolve the HEAD in " + root, e);
      return null;
    }
  }

  private static byte[] loadRevisionCatchingErrors(@NotNull GitFileRevision revision) throws VcsException, IOException {
    try {
      return revision.getContent();
    } catch (VcsException e) {
      String m = e.getMessage().trim();
      if (m.startsWith("fatal: ambiguous argument ")
          || (m.startsWith("fatal: Path '") && m.contains("' exists on disk, but not in '"))
          || (m.contains("is in the index, but not at stage ")
          || (m.contains("bad revision")))) {
        return ArrayUtil.EMPTY_BYTE_ARRAY;
      }
      else {
        throw e;
      }
    }
  }

  /**
   * @return number for "yours" revision  (taking {@code reverse} flag in account)
   * @param root
   */
  private int yoursRevision(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? THEIRS_REVISION_NUM : YOURS_REVISION_NUM;
  }

  /**
   * @return number for "theirs" revision (taking {@code reverse} flag in account)
   * @param root
   */
  private int theirsRevision(@NotNull VirtualFile root) {
    return myReverseRoots.contains(root) ? YOURS_REVISION_NUM : THEIRS_REVISION_NUM;
  }

  @Override
  public void conflictResolvedForFile(@NotNull VirtualFile file) {
    try {
      GitFileUtils.addFiles(myProject, GitUtil.getGitRoot(file), file);
    }
    catch (VcsException e) {
      LOG.error("Confirming conflict resolution failed", e);
    }
  }

  @Override
  public boolean isBinary(@NotNull VirtualFile file) {
    return file.getFileType().isBinary();
  }

  @Override
  @NotNull
  public MergeSession createMergeSession(@NotNull List<VirtualFile> files) {
    return new MyMergeSession(files);
  }

  /**
   * The conflict descriptor
   */
  private static class Conflict {
    VirtualFile myFile;
    VirtualFile myRoot;
    Status myStatusTheirs;
    Status myStatusYours;

    enum Status {
      MODIFIED, // modified on the branch
      DELETED // deleted on the branch
    }
  }


  /**
   * The merge session, it queries conflict information.
   */
  private class MyMergeSession implements MergeSession {
    Map<VirtualFile, Conflict> myConflicts = new HashMap<>();

    MyMergeSession(List<VirtualFile> filesToMerge) {
      // get conflict type by the file
      try {
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : GitUtil.sortFilesByGitRoot(filesToMerge).entrySet()) {
          Map<String, Conflict> cs = new HashMap<>();
          VirtualFile root = e.getKey();
          List<VirtualFile> files = e.getValue();
          GitSimpleHandler h = new GitSimpleHandler(myProject, root, GitCommand.LS_FILES);
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
            if (source == theirsRevision(root)) {
              c.myStatusTheirs = Conflict.Status.MODIFIED;
            }
            else if (source == yoursRevision(root)) {
              c.myStatusYours = Conflict.Status.MODIFIED;
            }
            else if (source != ORIGINAL_REVISION_NUM) {
              throw new IllegalStateException("Unknown revision " + source + " for the file: " + file);
            }
          }
          for (VirtualFile f : files) {
            String path = VcsFileUtil.relativePath(root, f);
            Conflict c = cs.get(path);
            LOG.assertTrue(c != null, String.format("The conflict not found for file: %s(%s)%nFull ls-files output: %n%s%nAll files: %n%s",
                                                    f.getPath(), path, output, files));
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

    @NotNull
    @Override
    public ColumnInfo[] getMergeInfoColumns() {
      return new ColumnInfo[]{new StatusColumn(false), new StatusColumn(true)};
    }

    @Override
    public boolean canMerge(@NotNull VirtualFile file) {
      Conflict c = myConflicts.get(file);
      return c != null;
    }

    @Override
    public void conflictResolvedForFile(VirtualFile file, @NotNull Resolution resolution) {
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
        LOG.error("Unexpected exception during the git operation (" + file.getPath() + ")", e);
      }
    }

    /**
     * The column shows either "yours" or "theirs" status
     */
    class StatusColumn extends ColumnInfo<VirtualFile, String> {
      /**
       * if false, "yours" status is displayed, otherwise "theirs"
       */
      private final boolean myIsTheirs;

      public StatusColumn(boolean isTheirs) {
        super(isTheirs ? GitBundle.message("merge.tool.column.theirs.status") : GitBundle.message("merge.tool.column.yours.status"));
        myIsTheirs = isTheirs;
      }

      @Override
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
