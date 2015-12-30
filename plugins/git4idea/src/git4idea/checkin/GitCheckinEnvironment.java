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

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AutoPopupController;
import com.intellij.dvcs.DvcsCommitAdditionalComponent;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.ui.VcsPushDialog;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
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
import com.intellij.ui.*;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFiles;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class GitCheckinEnvironment implements CheckinEnvironment {
  private static final Logger log = Logger.getInstance(GitCheckinEnvironment.class.getName());
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

  public GitCheckinEnvironment(@NotNull Project project, @NotNull final VcsDirtyScopeManager dirtyScopeManager, final GitVcsSettings settings) {
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
    LinkedHashSet<String> messages = ContainerUtil.newLinkedHashSet();
    for (VirtualFile root : GitUtil.gitRoots(Arrays.asList(filesToCheckin))) {
      VirtualFile mergeMsg = root.findFileByRelativePath(GitRepositoryFiles.GIT_MERGE_MSG);
      VirtualFile squashMsg = root.findFileByRelativePath(GitRepositoryFiles.GIT_SQUASH_MSG);
      try {
        if (mergeMsg == null && squashMsg == null) {
          continue;
        }
        String encoding = GitConfigUtil.getCommitEncoding(myProject, root);
        if (mergeMsg != null) {
          messages.add(loadMessage(mergeMsg, encoding));
        }
        else {
          messages.add(loadMessage(squashMsg, encoding));
        }
      }
      catch (IOException e) {
        if (log.isDebugEnabled()) {
          log.debug("Unable to load merge message", e);
        }
      }
    }
    return DvcsUtil.joinMessagesOrNull(messages);
  }

  private static String loadMessage(@NotNull VirtualFile messageFile, @NotNull String encoding) throws IOException {
    return FileUtil.loadFile(new File(messageFile.getPath()), encoding);
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
    List<VcsException> exceptions = new ArrayList<VcsException>();
    Map<VirtualFile, Collection<Change>> sortedChanges = sortChangesByGitRoot(changes, exceptions);
    log.assertTrue(!sortedChanges.isEmpty(), "Trying to commit an empty list of changes: " + changes);
    for (Map.Entry<VirtualFile, Collection<Change>> entry : sortedChanges.entrySet()) {
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
                FilePath afterPath = change.getAfterRevision().getFile();
                FilePath beforePath = change.getBeforeRevision().getFile();
                added.add(afterPath);
                if (!GitFileUtils.shouldIgnoreCaseChange(afterPath.getPath(), beforePath.getPath())) {
                  removed.add(beforePath);
                }
                break;
              default:
                throw new IllegalStateException("Unknown change type: " + change.getType());
            }
          }
          try {
            try {
              Set<FilePath> files = new HashSet<FilePath>();
              files.addAll(added);
              files.addAll(removed);
              commit(myProject, root, files, messageFile, myNextCommitAuthor, myNextCommitAmend, myNextCommitAuthorDate);
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
          finally {
            if (!messageFile.delete()) {
              log.warn("Failed to remove temporary file: " + messageFile);
            }
          }
        }
        catch (VcsException e) {
          exceptions.add(cleanupExceptionText(e));
        }
      }
      catch (IOException ex) {
        //noinspection ThrowableInstanceNeverThrown
        exceptions.add(new VcsException("Creation of commit message file failed", ex));
      }
    }
    if (myNextCommitIsPushed != null && myNextCommitIsPushed.booleanValue() && exceptions.isEmpty()) {
      GitRepositoryManager manager = GitUtil.getRepositoryManager(myProject);
      Collection<GitRepository> repositories = GitUtil.getRepositoriesFromRoots(manager, sortedChanges.keySet());
      final List<GitRepository> preselectedRepositories = ContainerUtil.newArrayList(repositories);
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        public void run() {
          new VcsPushDialog(myProject, preselectedRepositories, GitBranchUtil.getCurrentRepository(myProject)).show();
        }
      });
    }
    return exceptions;
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
   *
   * @param project     a project
   * @param root        a vcs root
   * @param added       added files
   * @param removed     removed files
   * @param messageFile a message file for commit
   * @param author      an author
   * @param exceptions  the list of exceptions to report
   * @param partialOperation
   * @return true if merge commit was successful
   */
  private static boolean mergeCommit(final Project project,
                                     final VirtualFile root,
                                     final Set<FilePath> added,
                                     final Set<FilePath> removed,
                                     final File messageFile,
                                     final String author,
                                     List<VcsException> exceptions, @NotNull final PartialOperation partialOperation) {
    HashSet<FilePath> realAdded = new HashSet<FilePath>();
    HashSet<FilePath> realRemoved = new HashSet<FilePath>();
    // perform diff
    GitSimpleHandler diff = new GitSimpleHandler(project, root, GitCommand.DIFF);
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

      final List<FilePath> files = new ArrayList<FilePath>();
      files.addAll(realAdded);
      files.addAll(realRemoved);
      final Ref<Boolean> mergeAll = new Ref<Boolean>();
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
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.setStdoutSuppressed(false);
      handler.addParameters("-F", messageFile.getAbsolutePath());
      if (author != null) {
        handler.addParameters("--author=" + author);
      }
      handler.endOptions();
      handler.run();
      GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
      manager.updateRepository(root);
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return false;
    }
    return true;
  }

  /**
   * Check if commit has failed due to unfinished merge or cherry-pick.
   *
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

  private static void commit(Project project,
                             VirtualFile root,
                             Collection<FilePath> files,
                             File message,
                             final String nextCommitAuthor,
                             boolean nextCommitAmend, Date nextCommitAuthorDate)
    throws VcsException {
    boolean amend = nextCommitAmend;
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.COMMIT);
      handler.setStdoutSuppressed(false);
      if (amend) {
        handler.addParameters("--amend");
      }
      else {
        amend = true;
      }
      handler.addParameters("--only", "-F", message.getAbsolutePath());
      if (nextCommitAuthor != null) {
        handler.addParameters("--author=" + nextCommitAuthor);
      }
      if (nextCommitAuthorDate != null) {
        handler.addParameters("--date", COMMIT_DATE_FORMAT.format(nextCommitAuthorDate));
      }
      handler.endOptions();
      handler.addParameters(paths);
      handler.run();
    }
    if (!project.isDisposed()) {
      GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
      manager.updateRepository(root);
    }
  }

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
    Map<VirtualFile, Collection<Change>> result = new HashMap<VirtualFile, Collection<Change>>();
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
        changeList = new ArrayList<Change>();
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

  private class GitCheckinOptions extends DvcsCommitAdditionalComponent implements CheckinChangeListSpecificComponent {
    private final GitVcs myVcs;
    @NotNull private final EditorTextField myAuthorField;
    @Nullable private Date myAuthorDate;

    GitCheckinOptions(@NotNull final Project project, @NotNull CheckinProjectPanel panel) {
      super(project, panel);
      myVcs = GitVcs.getInstance(project);
      final Insets insets = new Insets(2, 2, 2, 2);
      // add authors drop down
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
      c.gridx = 1;
      c.gridy = 0;
      c.weightx = 1;
      c.fill = GridBagConstraints.HORIZONTAL;

      Set<String> authors = new HashSet<String>(getUsersList(project));
      ContainerUtil.addAll(authors, mySettings.getCommitAuthors());
      List<String> list = new ArrayList<String>(authors);
      Collections.sort(list);

      myAuthorField = createTextField(project, list);

      authorLabel.setLabelFor(myAuthorField);
      myAuthorField.setToolTipText(GitBundle.getString("commit.author.tooltip"));
      myPanel.add(myAuthorField, c);
    }

    @NotNull
    private EditorTextField createTextField(@NotNull Project project, @NotNull List<String> list) {
      TextFieldWithAutoCompletionListProvider<String> completionProvider = new TextFieldWithAutoCompletion.StringsCompletionProvider(list, null);
      return new TextFieldWithAutoCompletion<String>(project, completionProvider, true, null) {
        @Override
        protected EditorEx createEditor() {
          EditorEx editor = super.createEditor();
          editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, true);
          EditorCustomization customization = SpellCheckingEditorCustomizationProvider.getInstance().getDisabledCustomization();
          if (customization != null) {
            customization.customize(editor);
          }
          return editor;
        }
      };
    }

    @Override
    @NotNull
    protected Set<VirtualFile> getVcsRoots(@NotNull Collection<FilePath> filePaths) {
      return GitUtil.gitRoots(filePaths);
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

    @NotNull
    private List<String> getUsersList(@NotNull Project project) {
      VcsUserRegistry userRegistry = ServiceManager.getService(project, VcsUserRegistry.class);
      return ContainerUtil.map(userRegistry.getUsers(), new Function<VcsUser, String>() {
        @Override
        public String fun(VcsUser user) {
          return user.getName() + " <" + user.getEmail() + ">";
        }
      });
    }

    @Override
    public void refresh() {
      super.refresh();
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
      myNextCommitAmend = myAmend.isSelected();
      myNextCommitAuthorDate = myAuthorDate;
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
        String author = String.format("%s <%s>", commit.getAuthor().getName(), commit.getAuthor().getEmail());
        myAuthorField.setText(author);
        myAuthorDate = new Date(commit.getAuthorTime());
      }
      else {
        myAuthorField.setText(null);
        myAuthorDate = null;
      }
    }
  }


  public void setNextCommitIsPushed(Boolean nextCommitIsPushed) {
    myNextCommitIsPushed = nextCommitIsPushed;
  }
}
