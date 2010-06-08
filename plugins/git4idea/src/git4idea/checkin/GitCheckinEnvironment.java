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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitConvertFilesDialog;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Git environment for commit operations.
 */
public class GitCheckinEnvironment implements CheckinEnvironment {
  /**
   * the logger
   */
  private static final Logger log = Logger.getInstance(GitCheckinEnvironment.class.getName());
  /**
   * The project
   */
  private final Project myProject;
  /**
   * The project
   */
  private final GitVcsSettings mySettings;

  /**
   * The author for the next commit
   */
  private String myNextCommitAuthor = null;
  /**
   * The push option of the next commit
   */
  private Boolean myNextCommitIsPushed = null;
  /**
   * Dirty scope manager for the project
   */
  private final VcsDirtyScopeManager myDirtyScopeManager;
  /**
   * the file name prefix for commit message file
   */
  @NonNls private static final String GIT_COMMIT_MSG_FILE_PREFIX = "git-comit-msg-";
  /**
   * the file extension for commit message file
   */
  @NonNls private static final String GIT_COMMIT_MSG_FILE_EXT = ".txt";


  /**
   * A constructor
   *
   * @param project           a project
   * @param dirtyScopeManager a dirty scope manager
   * @param settings          the vcs settings
   */
  public GitCheckinEnvironment(@NotNull Project project,
                               @NotNull final VcsDirtyScopeManager dirtyScopeManager,
                               final GitVcsSettings settings) {
    myProject = project;
    myDirtyScopeManager = dirtyScopeManager;
    mySettings = settings;
  }

  /**
   * {@inheritDoc}
   */
  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  /**
   * {@inheritDoc}
   * @param panel
   * @param additionalDataConsumer
   */
  @Nullable
  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return new GitCheckinOptions();
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    StringBuilder rc = new StringBuilder();
    for (VirtualFile root : GitUtil.gitRoots(Arrays.asList(filesToCheckin))) {
      VirtualFile mergeMsg = root.findFileByRelativePath(".git/MERGE_MSG");
      VirtualFile squashMsg = root.findFileByRelativePath(".git/SQUASH_MSG");
      if (mergeMsg != null || squashMsg != null) {
        try {
          String encoding = GitConfigUtil.getCommitEncoding(myProject, root);
          if (mergeMsg != null) {
            rc.append(FileUtil.loadFileText(new File(mergeMsg.getPath()), encoding));
          }
          if (squashMsg != null) {
            rc.append(FileUtil.loadFileText(new File(squashMsg.getPath()), encoding));
          }
        }
        catch (IOException e) {
          if (log.isDebugEnabled()) {
            log.debug("Unable to load merge message", e);
          }
        }
      }
    }
    if (rc.length() != 0) {
      return rc.toString();
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public String getHelpId() {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public String getCheckinOperationName() {
    return GitBundle.getString("commit.action.name");
  }

  /**
   * {@inheritDoc}
   */
  public List<VcsException> commit(@NotNull List<Change> changes, @NotNull String message) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    Map<VirtualFile, List<Change>> sortedChanges = sortChangesByGitRoot(changes, exceptions);
    if (GitConvertFilesDialog.showDialogIfNeeded(myProject, mySettings, sortedChanges, exceptions)) {
      for (Map.Entry<VirtualFile, List<Change>> entry : sortedChanges.entrySet()) {
        Set<FilePath> files = new HashSet<FilePath>();
        final VirtualFile root = entry.getKey();
        try {
          File messageFile = createMessageFile(root, message);
          try {
            final Set<FilePath> added = new HashSet<FilePath>();
            final Set<FilePath> removed = new HashSet<FilePath>();
            for (Change change : entry.getValue()) {
              switch (change.getType()) {
                case NEW:
                case MODIFICATION:
                  added.add(change.getAfterRevision().getFile());
                  break;
                case DELETED:
                  removed.add(change.getBeforeRevision().getFile());
                  break;
                case MOVED:
                  added.add(change.getAfterRevision().getFile());
                  removed.add(change.getBeforeRevision().getFile());
                  break;
                default:
                  throw new IllegalStateException("Unknown change type: " + change.getType());
              }
            }
            try {
              if (updateIndex(myProject, root, added, removed, exceptions)) {
                try {
                  files.addAll(added);
                  files.addAll(removed);
                  commit(myProject, root, files, messageFile, myNextCommitAuthor);
                }
                catch (VcsException ex) {
                  if (!isMergeCommit(ex)) {
                    throw ex;
                  }
                  if (!mergeCommit(myProject, root, added, removed, messageFile, myNextCommitAuthor, exceptions)) {
                    throw ex;
                  }
                }
              }
            }
            finally {
              if (!messageFile.delete()) {
                log.warn("Failed to remove temporary file: " + messageFile);
              }
            }
            if (myNextCommitIsPushed != null && myNextCommitIsPushed.booleanValue()) {
              // push
              GitLineHandler pushHandler = GitPushUtils.preparePush(myProject, root);
              if (pushHandler != null) {
                Collection<VcsException> problems = GitHandlerUtil.doSynchronouslyWithExceptions(pushHandler);
                for (VcsException e : problems) {
                  if (!isNoOrigin(e)) {
                    // no origin exception just means that push was not applicable to the repository
                    exceptions.add(e);
                  }
                }
              }
            }
          }
          catch (VcsException e) {
            exceptions.add(e);
          }
        }
        catch (IOException ex) {
          //noinspection ThrowableInstanceNeverThrown
          exceptions.add(new VcsException("Creation of commit message file failed", ex));
        }
      }
    }
    return exceptions;
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment, @NotNull NullableFunction<Object, Object> parametersHolder) {
    return commit(changes, preparedComment);
  }

  /**
   * Preform a merge commit
   *
   * @param project     a project
   * @param root        a vcs root
   * @param added       added files
   * @param removed     removed files
   * @param messageFile a message file for commit
   * @param author      an author
   * @param exceptions  the list of exceptions to report
   * @return true if merge commit was successful
   */
  private static boolean mergeCommit(final Project project,
                                     final VirtualFile root,
                                     final Set<FilePath> added,
                                     final Set<FilePath> removed,
                                     final File messageFile,
                                     final String author,
                                     List<VcsException> exceptions) {
    HashSet<FilePath> realAdded = new HashSet<FilePath>();
    HashSet<FilePath> realRemoved = new HashSet<FilePath>();
    // perform diff
    GitSimpleHandler diff = new GitSimpleHandler(project, root, GitCommand.DIFF);
    diff.setNoSSH(true);
    diff.setSilent(true);
    diff.setStdoutSuppressed(true);
    diff.addParameters("--diff-filter=ADMRUX", "--name-status", "HEAD");
    diff.endOptions();
    String output;
    try {
      output = diff.run();
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return false;
    }
    String rootPath = root.getPath();
    for (StringTokenizer lines = new StringTokenizer(output, "\n", false); lines.hasMoreTokens();) {
      String line = lines.nextToken().trim();
      if (line.length() == 0) {
        continue;
      }
      String[] tk = line.split("[ \t]+");
      switch (tk[0].charAt(0)) {
        case 'M':
        case 'A':
          realAdded.add(VcsUtil.getFilePath(rootPath + "/" + tk[tk.length - 1]));
          break;
        case 'D':
          realRemoved.add(VcsUtil.getFilePathForDeletedFile(rootPath + "/" + tk[tk.length - 1], false));
          break;
        default:
          throw new IllegalStateException("Unexpected status: " + line);
      }
    }
    realAdded.removeAll(added);
    realRemoved.removeAll(removed);
    if (realAdded.size() != 0 || realRemoved.size() != 0) {
      TreeSet<String> files = new TreeSet<String>();
      for (FilePath f : realAdded) {
        files.add(f.getPresentableUrl());
      }
      for (FilePath f : realRemoved) {
        files.add(f.getPresentableUrl());
      }
      final StringBuilder fileList = new StringBuilder();
      for (String f : files) {
        //noinspection HardCodedStringLiteral
        fileList.append("<li>");
        fileList.append(StringUtil.escapeXml(f));
        fileList.append("</li>");
      }
      final int[] rc = new int[1];
      try {
        EventQueue.invokeAndWait(new Runnable() {
          public void run() {
            rc[0] = Messages.showOkCancelDialog(project, GitBundle.message("commit.partial.merge.message", fileList.toString()),
                                                GitBundle.getString("commit.partial.merge.title"), null);

          }
        }                       );
      }
      catch (RuntimeException ex) {
        throw ex;
      }
      catch (Exception ex) {
        throw new RuntimeException("Unable to invoke a message box on AWT thread", ex);
      }
      if (rc[0] != 0) {
        return false;
      }
      // update non-indexed files
      if (!updateIndex(project, root, realAdded, realRemoved, exceptions)) {
        return false;
      }
      for (FilePath f : realAdded) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(f);
      }
      for (FilePath f : realRemoved) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(f);
      }
    }
    // perform merge commit
    try {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.setNoSSH(true);
      handler.addParameters("-F", messageFile.getAbsolutePath());
      if (author != null) {
        handler.addParameters("--author=" + author);
      }
      handler.endOptions();
      handler.run();
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return false;
    }
    return true;
  }

  /**
   * Check if commit has failed due to unfinished merge
   *
   * @param ex an exception to examine
   * @return true if exception means that there is a partial commit during merge
   */
  private static boolean isMergeCommit(final VcsException ex) {
    //noinspection HardCodedStringLiteral
    return -1 != ex.getMessage().indexOf("fatal: cannot do a partial commit during a merge.");
  }

  /**
   * Check if the exception means that no origin was found for pus operation
   *
   * @param ex an exception to use
   * @return true if exception means that changes cannot be pushed because repository is entirely local.
   */
  private static boolean isNoOrigin(final VcsException ex) {
    //noinspection HardCodedStringLiteral
    return ex.getMessage().indexOf("': unable to chdir or not a git archive") != -1;
  }

  /**
   * Update index (delete and remove files)
   *
   * @param project    the project
   * @param root       a vcs root
   * @param added      added/modified files to commit
   * @param removed    removed files to commit
   * @param exceptions a list of exceptions to update
   * @return true if index was updated successfully
   */
  private static boolean updateIndex(final Project project,
                                     final VirtualFile root,
                                     final Collection<FilePath> added,
                                     final Collection<FilePath> removed,
                                     final List<VcsException> exceptions) {
    boolean rc = true;
    if (!added.isEmpty()) {
      try {
        GitFileUtils.addPaths(project, root, added);
      }
      catch (VcsException ex) {
        exceptions.add(ex);
        rc = false;
      }
    }
    if (!removed.isEmpty()) {
      try {
        GitFileUtils.delete(project, root, removed, "--ignore-unmatch");
      }
      catch (VcsException ex) {
        exceptions.add(ex);
        rc = false;
      }
    }
    return rc;
  }

  /**
   * Create a file that contains the specified message
   *
   * @param root    a git repository root
   * @param message a message to write
   * @return a file reference
   * @throws IOException if file cannot be created
   */
  private File createMessageFile(VirtualFile root, final String message) throws IOException {
    // filter comment lines
    File file = File.createTempFile(GIT_COMMIT_MSG_FILE_PREFIX, GIT_COMMIT_MSG_FILE_EXT);
    file.deleteOnExit();
    @NonNls String encoding = GitConfigUtil.getCommitEncoding(myProject, root);
    Writer out = new OutputStreamWriter(new FileOutputStream(file), encoding);
    try {
      out.write(message);
    }
    finally {
      out.close();
    }
    return file;
  }

  /**
   * {@inheritDoc}
   */
  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    ArrayList<VcsException> rc = new ArrayList<VcsException>();
    Map<VirtualFile, List<FilePath>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilePathsByGitRoot(files);
    }
    catch (VcsException e) {
      rc.add(e);
      return rc;
    }
    for (Map.Entry<VirtualFile, List<FilePath>> e : sortedFiles.entrySet()) {
      try {
        final VirtualFile root = e.getKey();
        GitFileUtils.delete(myProject, root, e.getValue());
        markRootDirty(root);
      }
      catch (VcsException ex) {
        rc.add(ex);
      }
    }
    return rc;
  }

  /**
   * Prepare delete files handler.
   *
   * @param project          the project
   * @param root             a vcs root
   * @param files            a files to commit
   * @param message          a message file to use
   * @param nextCommitAuthor a author for the next commit
   * @return a simple handler that does the task
   * @throws VcsException in case of git problem
   */
  private static void commit(Project project,
                             VirtualFile root,
                             Collection<FilePath> files,
                             File message,
                             final String nextCommitAuthor) throws VcsException {
    boolean isFirst = true;
    for (List<String> paths : GitFileUtils.chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.setNoSSH(true);
      if (isFirst) {
        isFirst = false;
      } else {
        handler.addParameters("--amend");
      }
      handler.addParameters("--only", "-F", message.getAbsolutePath());
      if (nextCommitAuthor != null) {
        handler.addParameters("--author=" + nextCommitAuthor);
      }
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
  }


  /**
   * {@inheritDoc}
   */
  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    ArrayList<VcsException> rc = new ArrayList<VcsException>();
    Map<VirtualFile, List<VirtualFile>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilesByGitRoot(files);
    }
    catch (VcsException e) {
      rc.add(e);
      return rc;
    }
    for (Map.Entry<VirtualFile, List<VirtualFile>> e : sortedFiles.entrySet()) {
      try {
        final VirtualFile root = e.getKey();
        GitFileUtils.addFiles(myProject, root, e.getValue());
        markRootDirty(root);
      }
      catch (VcsException ex) {
        rc.add(ex);
      }
    }
    return rc;
  }

  /**
   * Sort changes by roots
   *
   * @param changes    a change list
   * @param exceptions exceptions to collect
   * @return sorted changes
   */
  private static Map<VirtualFile, List<Change>> sortChangesByGitRoot(@NotNull List<Change> changes, List<VcsException> exceptions) {
    Map<VirtualFile, List<Change>> result = new HashMap<VirtualFile, List<Change>>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      final ContentRevision beforeRevision = change.getBeforeRevision();
      // nothing-to-nothing change cannot happen.
      assert beforeRevision != null || afterRevision != null;
      // note that any path will work, because changes could happen within single vcs root
      final FilePath filePath = afterRevision != null ? afterRevision.getFile() : beforeRevision.getFile();
      final VirtualFile vcsRoot;
      try {
        // the parent paths for calculating roots in order to account for submodules that contribute
        // to the parent change. The path "." is never is valid change, so there should be no problem
        // with it.
        vcsRoot = GitUtil.getGitRoot(filePath.getParentPath());
      }
      catch (VcsException e) {
        exceptions.add(e);
        continue;
      }
      List<Change> changeList = result.get(vcsRoot);
      if (changeList == null) {
        changeList = new ArrayList<Change>();
        result.put(vcsRoot, changeList);
      }
      changeList.add(change);
    }
    return result;
  }

  /**
   * Mark root as dirty
   *
   * @param root a vcs root to rescan
   */
  private void markRootDirty(final VirtualFile root) {
    // Note that the root is invalidated because changes are detected per-root anyway.
    // Otherwise it is not possible to detect moves.
    myDirtyScopeManager.dirDirtyRecursively(root);
  }

  /**
   * Checkin options for git
   */
  private class GitCheckinOptions implements RefreshableOnComponent {
    /**
     * A container panel
     */
    private final JPanel myPanel;
    /**
     * The author ComboBox, the combobox contains previously selected authors.
     */
    private final JComboBox myAuthor;

    /**
     * A constructor
     */
    GitCheckinOptions() {
      myPanel = new JPanel(new GridBagLayout());
      final Insets insets = new Insets(2, 2, 2, 2);
      GridBagConstraints c = new GridBagConstraints();
      c.gridx = 0;
      c.gridy = 0;
      c.anchor = GridBagConstraints.WEST;
      c.insets = insets;
      final JLabel authorLabel = new JLabel(GitBundle.message("commit.author"));
      myPanel.add(authorLabel, c);
      c = new GridBagConstraints();
      c.anchor = GridBagConstraints.CENTER;
      c.insets = insets;
      c.gridx = 0;
      c.gridy = 1;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      myAuthor = new JComboBox(mySettings.PREVIOUS_COMMIT_AUTHORS);
      myAuthor.insertItemAt("", 0);
      myAuthor.setSelectedItem("");
      myAuthor.setEditable(true);
      authorLabel.setLabelFor(myAuthor);
      myAuthor.setToolTipText(GitBundle.getString("commit.author.tooltip"));
      myPanel.add(myAuthor, c);
    }

    /**
     * {@inheritDoc}
     */
    public JComponent getComponent() {
      return myPanel;
    }

    /**
     * {@inheritDoc}
     */
    public void refresh() {
      myAuthor.setSelectedItem("");
      myNextCommitAuthor = null;
      myNextCommitIsPushed = null;
    }

    /**
     * {@inheritDoc}
     */
    public void saveState() {
      String author = (String)myAuthor.getSelectedItem();
      myNextCommitAuthor = author.length() == 0 ? null : author;
      if (author.length() == 0) {
        myNextCommitAuthor = null;
      }
      else {
        myNextCommitAuthor = author;
        mySettings.saveCommitAuthor(author);
      }
    }

    /**
     * {@inheritDoc}
     */
    public void restoreState() {
      refresh();
    }
  }

  public void setNextCommitIsPushed(Boolean nextCommitIsPushed) {
    myNextCommitIsPushed = nextCommitIsPushed;
  }
}
