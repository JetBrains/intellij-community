/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions and limitations under the License.
 *
 * Copyright 2007 Decentrix Inc
 * Copyright 2007 Aspiro AS
 * Copyright 2008 MQSoftware
 * Copyright 2008 JetBrains s.r.o.
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */
package git4idea.checkin;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
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
import git4idea.GitUtil;
import git4idea.commands.GitHandlerUtil;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
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
  @NonNls private static final String GIT_COMIT_MSG_FILE_PREFIX = "git-comit-msg-";
  /**
   * the file extension for commit message file
   */
  @NonNls private static final String GIT_COMIT_MSG_FILE_EXT = ".txt";


  /**
   * A constructor
   *
   * @param project           a project
   * @param dirtyScopeManager a dirty scope manager
   * @param settings
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
    // TODO review it later
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel) {
    return new GitCheckinOptions();
  }

  /**
   * {@inheritDoc}
   */
  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    return GitBundle.getString("git.default.commit.message");
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
  @SuppressWarnings({"ConstantConditions"})
  public List<VcsException> commit(@NotNull List<Change> changes, @NotNull String message) {
    List<VcsException> exceptions = new ArrayList<VcsException>();
    try {
      File messageFile = createMessageFile(message);
      try {
        Map<VirtualFile, List<Change>> sortedChanges = sortChangesByVcsRoot(changes);
        for (VirtualFile root : sortedChanges.keySet()) {
          Set<FilePath> files = new HashSet<FilePath>();
          for (Change change : changes) {
            switch (change.getType()) {
              case NEW:
              case MODIFICATION:
                files.add(change.getAfterRevision().getFile());
                break;
              case DELETED:
                files.add(change.getBeforeRevision().getFile());
                break;
              case MOVED:
                files.add(change.getAfterRevision().getFile());
                files.add(change.getBeforeRevision().getFile());
                break;
              default:
                throw new IllegalStateException("Unknown change type: " + change.getType());
            }
          }
          try {
            if (updateIndex(myProject, root, files, exceptions)) {
              commit(myProject, root, files, messageFile, myNextCommitAuthor).run();
            }
            if (myNextCommitIsPushed != null && myNextCommitIsPushed.booleanValue()) {
              // push
              try {
                GitHandlerUtil
                  .doSynchronouslyWithException(GitPushUtils.preparePush(myProject, root), GitBundle.message("pushing.all.changes"));
              }
              catch (VcsException ex) {
                if (!isNoOrigin(ex)) {
                  throw ex;
                }
              }
            }
          }
          catch (VcsException e) {
            exceptions.add(e);
          }
        }
      }
      finally {
        if (!messageFile.delete()) {
          log.warn("Failed to remove temporary file: " + messageFile);
        }
      }
    }
    catch (IOException ex) {
      exceptions.add(new VcsException("Creation of commit message file failed", ex));
    }

    return exceptions;
  }

  /**
   * Check if the exception means that no origin was found for pus operation
   *
   * @param ex an exception to use
   * @return true if exception means that canges cannot be pushed because repository is entirely local.
   */
  private static boolean isNoOrigin(final VcsException ex) {
    return ex.getMessage().indexOf("': unable to chdir or not a git archive") != -1;
  }

  /**
   * Update index (delete and remove files)
   *
   * @param project    the project
   * @param root       a vcs root
   * @param files      a files to commit
   * @param exceptions a list of exceptions to update
   * @return true if index was updated successfully
   */
  private static boolean updateIndex(final Project project,
                                     final VirtualFile root,
                                     final Set<FilePath> files,
                                     final List<VcsException> exceptions) {
    ArrayList<FilePath> added = new ArrayList<FilePath>();
    ArrayList<FilePath> removed = new ArrayList<FilePath>();
    boolean rc = true;
    for (FilePath file : files) {
      if (file.getIOFile().exists()) {
        added.add(file);
      }
      else {
        removed.add(file);
      }
    }
    if (!added.isEmpty()) {
      try {
        GitSimpleHandler.addPaths(project, root, added).run();
      }
      catch (VcsException ex) {
        exceptions.add(ex);
        rc = false;
      }
    }
    if (!removed.isEmpty()) {
      try {
        GitSimpleHandler.delete(project, root, removed).run();
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
   * @param message a message to write
   * @return a file reference
   * @throws IOException
   */
  private static File createMessageFile(final String message) throws IOException {
    // filter comment lines
    StringBuilder filteredMessage = new StringBuilder(message.length());
    for (StringTokenizer stk = new StringTokenizer(message, "\n"); stk.hasMoreTokens();) {
      String line = stk.nextToken();
      if (line.charAt(0) == '#') {
        continue;
      }
      filteredMessage.append(line).append('\n');
    }
    File file = File.createTempFile(GIT_COMIT_MSG_FILE_PREFIX, GIT_COMIT_MSG_FILE_EXT);
    file.deleteOnExit();
    // TODO use repository encoding
    Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
    try {
      out.write(filteredMessage.toString());
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
    Map<VirtualFile, List<FilePath>> sortedFiles = GitUtil.sortFilePathsByVcsRoot(myProject, files);
    for (Map.Entry<VirtualFile, List<FilePath>> e : sortedFiles.entrySet()) {
      try {
        final VirtualFile root = e.getKey();
        GitSimpleHandler.delete(myProject, root, e.getValue()).run();
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
   * @param nextCommitAuthor
   * @return a simple handler that does the task
   */
  public static GitSimpleHandler commit(Project project,
                                        VirtualFile root,
                                        Collection<FilePath> files,
                                        File message,
                                        final String nextCommitAuthor) {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, "commit");
    handler.addParameters("--only", "-F", message.getAbsolutePath());
    if (nextCommitAuthor != null) {
      handler.addParameters("--author=" + nextCommitAuthor);
    }
    handler.endOptions();
    handler.addRelativePaths(files);
    handler.setNoSSH(true);
    return handler;
  }


  /**
   * {@inheritDoc}
   */
  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    ArrayList<VcsException> rc = new ArrayList<VcsException>();
    Map<VirtualFile, List<VirtualFile>> sortedFiles = GitUtil.sortFilesByVcsRoot(myProject, files);
    for (Map.Entry<VirtualFile, List<VirtualFile>> e : sortedFiles.entrySet()) {
      try {
        final VirtualFile root = e.getKey();
        GitSimpleHandler.addFiles(myProject, root, e.getValue()).run();
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
   * @param changes a change list
   * @return sorted changes
   */
  private Map<VirtualFile, List<Change>> sortChangesByVcsRoot(@NotNull List<Change> changes) {
    Map<VirtualFile, List<Change>> result = new HashMap<VirtualFile, List<Change>>();
    for (Change change : changes) {
      final ContentRevision afterRevision = change.getAfterRevision();
      final ContentRevision beforeRevision = change.getBeforeRevision();
      // nothing-to-nothing change cannot happen.
      assert beforeRevision != null || afterRevision != null;
      // note that any path will work, because changes could happen within single vcs root
      final FilePath filePath = afterRevision != null ? afterRevision.getFile() : beforeRevision.getFile();
      final VirtualFile vcsRoot = GitUtil.getGitRoot(myProject, filePath);
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
    private JPanel myPanel;
    /**
     * If checked, the changes are pushed to the server as well as connected.
     */
    private JCheckBox myPushChanges;
    /**
     * The author ComboBox, the dropdown contains previously selected authors.
     */
    private JComboBox myAuthor;

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
      myPushChanges = new JCheckBox(GitBundle.message("commit.push.changes"));
      myPushChanges.setToolTipText(GitBundle.getString("commit.push.changes.tooltip"));
      // disable non-working functionality
      // myPanel.add(myPushChanges, c);
      c = new GridBagConstraints();
      c.anchor = GridBagConstraints.WEST;
      c.insets = insets;
      c.gridx = 0;
      c.gridy = 1;
      final JLabel authorLabel = new JLabel(GitBundle.message("commit.author"));
      myPanel.add(authorLabel, c);
      c = new GridBagConstraints();
      c.anchor = GridBagConstraints.CENTER;
      c.insets = insets;
      c.gridx = 0;
      c.gridy = 2;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      myAuthor = new JComboBox(mySettings.PREVIOUS_COMMIT_AUTHORS);
      myAuthor.addItem("");
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
      myPushChanges.setSelected(false);
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
      myNextCommitIsPushed = myPushChanges.isSelected();
    }

    /**
     * {@inheritDoc}
     */
    public void restoreState() {
      refresh();
    }
  }
}
