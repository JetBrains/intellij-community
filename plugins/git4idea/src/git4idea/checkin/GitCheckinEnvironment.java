/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.dvcs.AmendComponent;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.SelectFilePathsDialog;
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUserRegistry;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.util.text.StringUtil.escapeXml;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getAfterPath;
import static com.intellij.openapi.vcs.changes.ChangesUtil.getBeforePath;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.GitUtil.getLogString;
import static git4idea.GitUtil.getRepositoryManager;
import static java.util.Arrays.asList;

public class GitCheckinEnvironment implements CheckinEnvironment {
  private static final Logger LOG = Logger.getInstance(GitCheckinEnvironment.class);
  @NonNls private static final String GIT_COMMIT_MSG_FILE_PREFIX = "git-commit-msg-"; // the file name prefix for commit message file
  @NonNls private static final String GIT_COMMIT_MSG_FILE_EXT = ".txt"; // the file extension for commit message file

  private final Project myProject;
  public static final SimpleDateFormat COMMIT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final GitVcsSettings mySettings;

  private String myNextCommitAuthor = null; // The author for the next commit
  private boolean myNextCommitAmend; // If true, the next commit is amended
  private Boolean myNextCommitIsPushed = null; // The push option of the next commit
  private Date myNextCommitAuthorDate;
  private boolean myNextCommitSignOff;

  public GitCheckinEnvironment(@NotNull Project project,
                               @NotNull final VcsDirtyScopeManager dirtyScopeManager,
                               final GitVcsSettings settings) {
    myProject = project;
    myDirtyScopeManager = dirtyScopeManager;
    mySettings = settings;
  }

  public boolean keepChangeListAfterCommit(ChangeList changeList) {
    return false;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  @Nullable
  public RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel,
                                                             PairConsumer<Object, Object> additionalDataConsumer) {
    return new GitCheckinOptions(myProject, panel);
  }

  @Nullable
  public String getDefaultMessageFor(FilePath[] filesToCheckin) {
    LinkedHashSet<String> messages = newLinkedHashSet();
    GitRepositoryManager manager = getRepositoryManager(myProject);
    for (VirtualFile root : GitUtil.gitRoots(asList(filesToCheckin))) {
      GitRepository repository = manager.getRepositoryForRoot(root);
      if (repository == null) { // unregistered nested submodule found by GitUtil.getGitRoot
        LOG.warn("Unregistered repository: " + root);
        continue;
      }
      File mergeMsg = repository.getRepositoryFiles().getMergeMessageFile();
      File squashMsg = repository.getRepositoryFiles().getSquashMessageFile();
      try {
        if (!mergeMsg.exists() && !squashMsg.exists()) {
          continue;
        }
        String encoding = GitConfigUtil.getCommitEncoding(myProject, root);
        if (mergeMsg.exists()) {
          messages.add(loadMessage(mergeMsg, encoding));
        }
        else {
          messages.add(loadMessage(squashMsg, encoding));
        }
      }
      catch (IOException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Unable to load merge message", e);
        }
      }
    }
    return DvcsUtil.joinMessagesOrNull(messages);
  }

  private static String loadMessage(@NotNull File messageFile, @NotNull String encoding) throws IOException {
    return FileUtil.loadFile(messageFile, encoding);
  }

  public String getHelpId() {
    return null;
  }

  public String getCheckinOperationName() {
    return GitBundle.getString("commit.action.name");
  }

  public List<VcsException> commit(@NotNull List<Change> changes,
                                   @NotNull String message,
                                   @NotNull NullableFunction<Object, Object> parametersHolder, Set<String> feedback) {
    List<VcsException> exceptions = new ArrayList<>();
    Map<VirtualFile, Collection<Change>> sortedChanges = sortChangesByGitRoot(changes, exceptions);
    LOG.assertTrue(!sortedChanges.isEmpty(), "Trying to commit an empty list of changes: " + changes);
    for (Map.Entry<VirtualFile, Collection<Change>> entry : sortedChanges.entrySet()) {
      VirtualFile root = entry.getKey();
      File messageFile;
      try {
        messageFile = createMessageFile(root, message);
      }
      catch (IOException ex) {
        //noinspection ThrowableInstanceNeverThrown
        exceptions.add(new VcsException("Creation of commit message file failed", ex));
        continue;
      }

      Set<FilePath> added = new HashSet<>();
      Set<FilePath> removed = new HashSet<>();
      final Set<Change> caseOnlyRenames = new HashSet<>();
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
            FilePath afterPath = change.getAfterRevision().getFile();
            FilePath beforePath = change.getBeforeRevision().getFile();
            if (!SystemInfo.isFileSystemCaseSensitive && GitUtil.isCaseOnlyChange(beforePath.getPath(), afterPath.getPath())) {
              caseOnlyRenames.add(change);
            }
            else {
              added.add(afterPath);
              removed.add(beforePath);
            }
            break;
          default:
            throw new IllegalStateException("Unknown change type: " + change.getType());
        }
      }

      try {
        if (!caseOnlyRenames.isEmpty()) {
          List<VcsException> exs = commitWithCaseOnlyRename(myProject, root, caseOnlyRenames, added, removed,
                                                            messageFile, myNextCommitAuthor);
          exceptions.addAll(map(exs, GitCheckinEnvironment::cleanupExceptionText));
        }
        else {
          try {
            Set<FilePath> files = new HashSet<>();
            files.addAll(added);
            files.addAll(removed);
            commit(myProject, root, files, messageFile);
          }
          catch (VcsException ex) {
            PartialOperation partialOperation = isMergeCommit(ex);
            if (partialOperation == PartialOperation.NONE) {
              throw ex;
            }
            if (!mergeCommit(myProject, root, added, removed, messageFile, myNextCommitAuthor, exceptions, partialOperation)) {
              throw ex;
            }
          }
        }
      }
      catch (VcsException e) {
        exceptions.add(cleanupExceptionText(e));
      }
      finally {
        if (!messageFile.delete()) {
          LOG.warn("Failed to remove temporary file: " + messageFile);
        }
      }
    }
    if (myNextCommitIsPushed != null && myNextCommitIsPushed.booleanValue() && exceptions.isEmpty()) {
      GitRepositoryManager manager = getRepositoryManager(myProject);
      Collection<GitRepository> repositories = GitUtil.getRepositoriesFromRoots(manager, sortedChanges.keySet());
      final List<GitRepository> preselectedRepositories = newArrayList(repositories);
      GuiUtils.invokeLaterIfNeeded(() ->
        new VcsPushDialog(myProject, preselectedRepositories, GitBranchUtil.getCurrentRepository(myProject)).show(),
        ModalityState.defaultModalityState());
    }
    return exceptions;
  }

  @NotNull
  private List<VcsException> commitWithCaseOnlyRename(@NotNull Project project,
                                                      @NotNull VirtualFile root,
                                                      @NotNull Set<Change> caseOnlyRenames,
                                                      @NotNull Set<FilePath> added,
                                                      @NotNull Set<FilePath> removed,
                                                      @NotNull File messageFile,
                                                      @Nullable String author) {
    String rootPath = root.getPath();
    LOG.info("Committing case only rename: " + getLogString(rootPath, caseOnlyRenames) + " in " + getShortRepositoryName(project, root));

    // 1. Check what is staged besides case-only renames
    Collection<Change> stagedChanges;
    try {
      stagedChanges = GitChangeUtils.getStagedChanges(project, root);
      LOG.debug("Found staged changes: " + getLogString(rootPath, stagedChanges));
    }
    catch (VcsException e) {
      return Collections.singletonList(e);
    }

    // 2. Reset staged changes which are not selected for commit
    Collection<Change> excludedStagedChanges = filter(stagedChanges, change ->
      !caseOnlyRenames.contains(change) && !added.contains(getAfterPath(change)) && !removed.contains(getBeforePath(change)));
    if (!excludedStagedChanges.isEmpty()) {
      LOG.info("Staged changes excluded for commit: " + getLogString(rootPath, excludedStagedChanges));
      try {
        reset(project, root, excludedStagedChanges);
      }
      catch (VcsException e) {
        return Collections.singletonList(e);
      }
    }

    List<VcsException> exceptions = new ArrayList<>();
    try {
      // 3. Stage what else is needed to commit
      List<FilePath> newPathsOfCaseRenames = map(caseOnlyRenames, ChangesUtil::getAfterPath);
      LOG.debug("Updating index for added:" + added + "\n, removed: " + removed + "\n, and case-renames: " + newPathsOfCaseRenames);
      Set<FilePath> toAdd = new HashSet<>(added);
      toAdd.addAll(newPathsOfCaseRenames);
      updateIndex(project, root, toAdd, removed, exceptions);
      if (!exceptions.isEmpty()) return exceptions;

      // 4. Commit the staging area
      LOG.debug("Performing commit...");
      try {
        commitWithoutPaths(project, root, messageFile, author);
      }
      catch (VcsException e) {
        return Collections.singletonList(e);
      }
    }
    finally {
      // 5. Stage back the changes unstaged before commit
      if (!excludedStagedChanges.isEmpty()) {
        LOG.debug("Restoring changes which were unstaged before commit: " + getLogString(rootPath, excludedStagedChanges));
        Set<FilePath> toAdd = map2SetNotNull(excludedStagedChanges, ChangesUtil::getAfterPath);
        Condition<Change> isMovedOrDeleted = change -> change.getType() == Change.Type.MOVED || change.getType() == Change.Type.DELETED;
        Set<FilePath> toRemove = map2SetNotNull(filter(excludedStagedChanges, isMovedOrDeleted), ChangesUtil::getBeforePath);
        updateIndex(project, root, toAdd, toRemove, exceptions);
      }
    }
    return exceptions;
  }

  private static void reset(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<Change> changes) throws VcsException {
    Set<FilePath> paths = new HashSet<>();
    paths.addAll(mapNotNull(changes, ChangesUtil::getAfterPath));
    paths.addAll(mapNotNull(changes, ChangesUtil::getBeforePath));

    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.RESET);
    handler.endOptions();
    handler.addRelativePaths(paths);
    handler.run();
  }

  @NotNull
  private static VcsException cleanupExceptionText(VcsException original) {
    String msg = original.getMessage();
    msg = GitUtil.cleanupErrorPrefixes(msg);
    final String DURING_EXECUTING_SUFFIX = GitSimpleHandler.DURING_EXECUTING_ERROR_MESSAGE;
    int suffix = msg.indexOf(DURING_EXECUTING_SUFFIX);
    if (suffix > 0) {
      msg = msg.substring(0, suffix);
    }
    return new VcsException(msg.trim(), original.getCause());
  }

  public List<VcsException> commit(List<Change> changes, String preparedComment) {
    return commit(changes, preparedComment, FunctionUtil.nullConstant(), null);
  }

  /**
   * Preform a merge commit
   *
   * @param project          a project
   * @param root             a vcs root
   * @param added            added files
   * @param removed          removed files
   * @param messageFile      a message file for commit
   * @param author           an author
   * @param exceptions       the list of exceptions to report
   * @param partialOperation
   * @return true if merge commit was successful
   */
  private boolean mergeCommit(final Project project,
                              final VirtualFile root,
                              final Set<FilePath> added,
                              final Set<FilePath> removed,
                              final File messageFile,
                              final String author,
                              List<VcsException> exceptions, @NotNull final PartialOperation partialOperation) {
    HashSet<FilePath> realAdded = new HashSet<>();
    HashSet<FilePath> realRemoved = new HashSet<>();
    // perform diff
    GitSimpleHandler diff = new GitSimpleHandler(project, root, GitCommand.DIFF);
    diff.setSilent(true);
    diff.setStdoutSuppressed(true);
    diff.addParameters("--diff-filter=ADMRUX", "--name-status", "--no-renames", "HEAD");
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
    for (StringTokenizer lines = new StringTokenizer(output, "\n", false); lines.hasMoreTokens(); ) {
      String line = lines.nextToken().trim();
      if (line.length() == 0) {
        continue;
      }
      String[] tk = line.split("\t");
      switch (tk[0].charAt(0)) {
        case 'M':
        case 'A':
          realAdded.add(VcsUtil.getFilePath(rootPath + "/" + tk[1]));
          break;
        case 'D':
          realRemoved.add(VcsUtil.getFilePathForDeletedFile(rootPath + "/" + tk[1], false));
          break;
        default:
          throw new IllegalStateException("Unexpected status: " + line);
      }
    }
    realAdded.removeAll(added);
    realRemoved.removeAll(removed);
    if (realAdded.size() != 0 || realRemoved.size() != 0) {

      final List<FilePath> files = new ArrayList<>();
      files.addAll(realAdded);
      files.addAll(realRemoved);
      final Ref<Boolean> mergeAll = new Ref<>();
      try {
        GuiUtils.runOrInvokeAndWait(new Runnable() {
          public void run() {
            String message = GitBundle.message("commit.partial.merge.message", partialOperation.getName());
            SelectFilePathsDialog dialog = new SelectFilePathsDialog(project, files, message,
                                                                     null, "Commit All Files", CommonBundle.getCancelButtonText(), false);
            dialog.setTitle(GitBundle.getString("commit.partial.merge.title"));
            dialog.show();
            mergeAll.set(dialog.isOK());
          }
        });
      }
      catch (RuntimeException ex) {
        throw ex;
      }
      catch (Exception ex) {
        throw new RuntimeException("Unable to invoke a message box on AWT thread", ex);
      }
      if (!mergeAll.get()) {
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
      commitWithoutPaths(project, root, messageFile, author);
      GitRepositoryManager manager = getRepositoryManager(project);
      manager.updateRepository(root);
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return false;
    }
    return true;
  }

  private void commitWithoutPaths(@NotNull Project project,
                                  @NotNull VirtualFile root,
                                  @NotNull File messageFile,
                                  @Nullable String author) throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
    handler.setStdoutSuppressed(false);
    handler.addParameters("-F", messageFile.getAbsolutePath());
    if (author != null) {
      handler.addParameters("--author=" + author);
    }
    if (myNextCommitSignOff) {
      handler.addParameters("--signoff");
    }
    handler.endOptions();
    handler.run();
  }

  /**
   * Check if commit has failed due to unfinished merge or cherry-pick.
   *
   * @param ex an exception to examine
   * @return true if exception means that there is a partial commit during merge
   */
  private static PartialOperation isMergeCommit(final VcsException ex) {
    String message = ex.getMessage();
    if (message.contains("fatal: cannot do a partial commit during a merge")) {
      return PartialOperation.MERGE;
    }
    if (message.contains("fatal: cannot do a partial commit during a cherry-pick")) {
      return PartialOperation.CHERRY_PICK;
    }
    return PartialOperation.NONE;
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
    File file = FileUtil.createTempFile(GIT_COMMIT_MSG_FILE_PREFIX, GIT_COMMIT_MSG_FILE_EXT);
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

  public List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files) {
    ArrayList<VcsException> rc = new ArrayList<>();
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

  private void commit(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<FilePath> files, @NotNull File message)
    throws VcsException {
    boolean amend = myNextCommitAmend;
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.setStdoutSuppressed(false);
      if (myNextCommitSignOff) {
        handler.addParameters("--signoff");
      }
      if (amend) {
        handler.addParameters("--amend");
      }
      else {
        amend = true;
      }
      handler.addParameters("--only", "-F", message.getAbsolutePath());
      if (myNextCommitAuthor != null) {
        handler.addParameters("--author=" + myNextCommitAuthor);
      }
      if (myNextCommitAuthorDate != null) {
        handler.addParameters("--date", COMMIT_DATE_FORMAT.format(myNextCommitAuthorDate));
      }
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
    if (!project.isDisposed()) {
      GitRepositoryManager manager = getRepositoryManager(project);
      manager.updateRepository(root);
    }
  }

  public List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files) {
    ArrayList<VcsException> rc = new ArrayList<>();
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

  private enum PartialOperation {
    NONE("none"),
    MERGE("merge"),
    CHERRY_PICK("cherry-pick");

    private final String myName;

    PartialOperation(String name) {
      myName = name;
    }

    String getName() {
      return myName;
    }
  }

  private static Map<VirtualFile, Collection<Change>> sortChangesByGitRoot(@NotNull List<Change> changes, List<VcsException> exceptions) {
    Map<VirtualFile, Collection<Change>> result = new HashMap<>();
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
      Collection<Change> changeList = result.get(vcsRoot);
      if (changeList == null) {
        changeList = new ArrayList<>();
        result.put(vcsRoot, changeList);
      }
      changeList.add(change);
    }
    return result;
  }

  private void markRootDirty(final VirtualFile root) {
    // Note that the root is invalidated because changes are detected per-root anyway.
    // Otherwise it is not possible to detect moves.
    myDirtyScopeManager.dirDirtyRecursively(root);
  }

  public void reset() {
    myNextCommitAmend = false;
    myNextCommitAuthor = null;
    myNextCommitIsPushed = null;
    myNextCommitAuthorDate = null;
  }

  private class GitCheckinOptions implements CheckinChangeListSpecificComponent, RefreshableOnComponent  {
    @NotNull private final GitVcs myVcs;
    @NotNull private JPanel myPanel;
    @NotNull private final EditorTextField myAuthorField;
    @Nullable private Date myAuthorDate;
    @NotNull private AmendComponent myAmendComponent;
    @NotNull private final JCheckBox mySignOffCheckbox;

    GitCheckinOptions(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
      myVcs = assertNotNull(GitVcs.getInstance(project));

      myAuthorField = createTextField(project, getAuthors(project));
      myAuthorField.setToolTipText(GitBundle.getString("commit.author.tooltip"));
      JLabel authorLabel = new JBLabel(GitBundle.message("commit.author"));
      authorLabel.setLabelFor(myAuthorField);

      myAmendComponent = new MyAmendComponent(project, getRepositoryManager(project), panel);
      mySignOffCheckbox = new JBCheckBox("Sign-off commit", mySettings.shouldSignOffCommit());
      mySignOffCheckbox.setMnemonic(KeyEvent.VK_G);
      mySignOffCheckbox.setToolTipText(getToolTip(project, panel));

      GridBag gb = new GridBag().
        setDefaultAnchor(GridBagConstraints.WEST).
        setDefaultInsets(JBUI.insets(2));
      myPanel = new JPanel(new GridBagLayout());
      myPanel.add(authorLabel, gb.nextLine().next());
      myPanel.add(myAuthorField, gb.next().fillCellHorizontally().weightx(1));
      myPanel.add(mySignOffCheckbox, gb.nextLine().next().coverLine());
      myPanel.add(myAmendComponent.getComponent(), gb.nextLine().next().coverLine());
    }

    @NotNull
    private String getToolTip(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
      VcsUser user = getFirstItem(mapNotNull(panel.getRoots(), it -> GitUserRegistry.getInstance(project).getUser(it)));
      String signature = user != null ? escapeXml(VcsUserUtil.toExactString(user)) : "";
      return "<html>Adds the following line at the end of the commit message:<br/>" +
             "Signed-off by: " + signature + "</html>";
    }

    @NotNull
    private List<String> getAuthors(@NotNull Project project) {
      Set<String> authors = new HashSet<>(getUsersList(project));
      addAll(authors, mySettings.getCommitAuthors());
      List<String> list = new ArrayList<>(authors);
      Collections.sort(list);
      return list;
    }

    @NotNull
    private EditorTextField createTextField(@NotNull Project project, @NotNull List<String> list) {
      TextCompletionProvider completionProvider =
        new ValuesCompletionProviderDumbAware<>(new DefaultTextCompletionValueDescriptor.StringValueDescriptor(), list);
      return new TextFieldWithCompletion(project, completionProvider, "", true, true, true);
    }

    private class MyAmendComponent extends AmendComponent {
      public MyAmendComponent(@NotNull Project project, @NotNull GitRepositoryManager manager, @NotNull CheckinProjectPanel panel) {
        super(project, manager, panel);
      }

      @NotNull
      @Override
      protected Set<VirtualFile> getVcsRoots(@NotNull Collection<FilePath> files) {
        return GitUtil.gitRoots(files);
      }

      @Nullable
      @Override
      protected String getLastCommitMessage(@NotNull VirtualFile root) throws VcsException {
        GitSimpleHandler h = new GitSimpleHandler(myProject, root, GitCommand.LOG);
        h.addParameters("--max-count=1");
        String formatPattern;
        if (GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(myVcs.getVersion())) {
          formatPattern = "%B";
        }
        else {
          // only message: subject + body; "%-b" means that preceding line-feeds will be deleted if the body is empty
          // %s strips newlines from subject; there is no way to work around it before 1.7.2 with %B (unless parsing some fixed format)
          formatPattern = "%s%n%n%-b";
        }
        h.addParameters("--pretty=format:" + formatPattern);
        return h.run();
      }
    }

    @NotNull
    private List<String> getUsersList(@NotNull Project project) {
      VcsUserRegistry userRegistry = ServiceManager.getService(project, VcsUserRegistry.class);
      return map(userRegistry.getUsers(), VcsUserUtil::toExactString);
    }

    @Override
    public void refresh() {
      myAmendComponent.refresh();
      myAuthorField.setText(null);
      myAuthorDate = null;
      reset();
    }

    @Override
    public void saveState() {
      String author = myAuthorField.getText();
      if (StringUtil.isEmptyOrSpaces(author)) {
        myNextCommitAuthor = null;
      }
      else {
        myNextCommitAuthor = GitCommitAuthorCorrector.correct(author);
        mySettings.saveCommitAuthor(myNextCommitAuthor);
      }
      myNextCommitAmend = myAmendComponent.isAmend();
      myNextCommitAuthorDate = myAuthorDate;
      mySettings.setSignOffCommit(mySignOffCheckbox.isSelected());
      myNextCommitSignOff = mySignOffCheckbox.isSelected();
    }

    @Override
    public void restoreState() {
      refresh();
    }

    @Override
    public void onChangeListSelected(LocalChangeList list) {
      Object data = list.getData();
      if (data instanceof VcsFullCommitDetails) {
        VcsFullCommitDetails commit = (VcsFullCommitDetails)data;
        String author = VcsUserUtil.toExactString(commit.getAuthor());
        myAuthorField.setText(author);
        myAuthorDate = new Date(commit.getAuthorTime());
      }
      else {
        myAuthorField.setText(null);
        myAuthorDate = null;
      }
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }
  }


  public void setNextCommitIsPushed(Boolean nextCommitIsPushed) {
    myNextCommitIsPushed = nextCommitIsPushed;
  }
}
