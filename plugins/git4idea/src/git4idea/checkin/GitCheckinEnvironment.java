package git4idea.checkin;
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
import git4idea.commands.GitSimpleHandler;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;

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
  private Project myProject;
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
   */
  public GitCheckinEnvironment(@NotNull Project project, @NotNull final VcsDirtyScopeManager dirtyScopeManager) {
    myProject = project;
    myDirtyScopeManager = dirtyScopeManager;
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
    return null;
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
              commit(myProject, root, files, messageFile).run();
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
   * @param project the project
   * @param root    a vcs root
   * @param files   a files to commit
   * @param message a message file to use
   * @return a simple handler that does the task
   */
  public static GitSimpleHandler commit(Project project, VirtualFile root, Collection<FilePath> files, File message) {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, "commit");
    handler.addParameters("--only", "-F", message.getAbsolutePath());
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
      final VirtualFile vcsRoot = GitUtil.getVcsRoot(myProject, filePath);
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
}
