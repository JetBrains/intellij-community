/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.roots.VcsRootDetector;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.AnnotationProviderEx;
import com.intellij.vcs.log.VcsUserRegistry;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.annotate.GitRepositoryForAnnotationsListener;
import git4idea.changes.GitCommittedChangeListProvider;
import git4idea.changes.GitOutgoingChangesProvider;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.checkin.GitCommitAndPushExecutor;
import git4idea.checkout.GitCheckoutProvider;
import git4idea.commands.Git;
import git4idea.config.*;
import git4idea.diff.GitDiffProvider;
import git4idea.history.GitHistoryProvider;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeProvider;
import git4idea.rollback.GitRollbackEnvironment;
import git4idea.roots.GitIntegrationEnabler;
import git4idea.status.GitChangeProvider;
import git4idea.ui.branch.GitBranchWidget;
import git4idea.update.GitUpdateEnvironment;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static java.util.Comparator.comparing;

/**
 * Git VCS implementation
 */
public class GitVcs extends AbstractVcs<CommittedChangeList> {

  public static final String NAME = "Git";
  public static final String ID = "git";

  private static final Logger LOG = Logger.getInstance(GitVcs.class.getName());
  private static final VcsKey ourKey = createKey(NAME);

  @Nullable private final ChangeProvider myChangeProvider;
  @Nullable private final GitCheckinEnvironment myCheckinEnvironment;
  private final RollbackEnvironment myRollbackEnvironment;
  private final GitUpdateEnvironment myUpdateEnvironment;
  private final GitAnnotationProvider myAnnotationProvider;
  private final DiffProvider myDiffProvider;
  private final GitHistoryProvider myHistoryProvider;
  @NotNull private final Git myGit;
  private final ProjectLevelVcsManager myVcsManager;
  private final GitVcsApplicationSettings myAppSettings;
  private final Configurable myConfigurable;
  private final RevisionSelector myRevSelector;
  private final GitCommittedChangeListProvider myCommittedChangeListProvider;

  private GitVFSListener myVFSListener; // a VFS listener that tracks file addition, deletion, and renaming.

  private final ReadWriteLock myCommandLock = new ReentrantReadWriteLock(true); // The command read/write lock
  @Nullable private final GitCommitAndPushExecutor myCommitAndPushExecutor;
  private final GitExecutableValidator myExecutableValidator;
  private GitBranchWidget myBranchWidget;

  private GitVersion myVersion = GitVersion.NULL; // version of Git which this plugin uses.
  private static final int MAX_CONSOLE_OUTPUT_SIZE = 10000;
  private GitRepositoryForAnnotationsListener myRepositoryForAnnotationsListener;

  @Nullable
  public static GitVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    return (GitVcs) ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME);
  }

  public GitVcs(@NotNull Project project, @NotNull Git git,
                @NotNull final ProjectLevelVcsManager gitVcsManager,
                @NotNull final GitAnnotationProvider gitAnnotationProvider,
                @NotNull final GitDiffProvider gitDiffProvider,
                @NotNull final GitHistoryProvider gitHistoryProvider,
                @NotNull final GitRollbackEnvironment gitRollbackEnvironment,
                @NotNull final GitVcsApplicationSettings gitSettings,
                @NotNull final GitVcsSettings gitProjectSettings,
                @NotNull GitSharedSettings sharedSettings) {
    super(project, NAME);
    myGit = git;
    myVcsManager = gitVcsManager;
    myAppSettings = gitSettings;
    myChangeProvider = project.isDefault() ? null : ServiceManager.getService(project, GitChangeProvider.class);
    myCheckinEnvironment = project.isDefault() ? null : ServiceManager.getService(project, GitCheckinEnvironment.class);
    myAnnotationProvider = gitAnnotationProvider;
    myDiffProvider = gitDiffProvider;
    myHistoryProvider = gitHistoryProvider;
    myRollbackEnvironment = gitRollbackEnvironment;
    myRevSelector = new GitRevisionSelector();
    myConfigurable = new GitVcsConfigurable(myProject, gitProjectSettings, sharedSettings);
    myUpdateEnvironment = new GitUpdateEnvironment(myProject, gitProjectSettings);
    myCommittedChangeListProvider = new GitCommittedChangeListProvider(myProject);
    myOutgoingChangesProvider = new GitOutgoingChangesProvider(myProject);
    myCommitAndPushExecutor = myCheckinEnvironment != null ? new GitCommitAndPushExecutor(myCheckinEnvironment) : null;
    myExecutableValidator = new GitExecutableValidator(myProject);
  }


  public ReadWriteLock getCommandLock() {
    return myCommandLock;
  }

  /**
   * Run task in background using the common queue (per project)
   * @param task the task to run
   */
  public static void runInBackground(Task.Backgroundable task) {
    task.queue();
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return myCommittedChangeListProvider;
  }

  @Override
  public String getRevisionPattern() {
    // return the full commit hash pattern, possibly other revision formats should be supported as well
    return "[0-9a-fA-F]+";
  }

  @Override
  @Nullable
  public CheckinEnvironment createCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  @NotNull
  @Override
  public MergeProvider getMergeProvider() {
    return GitMergeProvider.detect(myProject);
  }

  @Override
  @NotNull
  public RollbackEnvironment createRollbackEnvironment() {
    return myRollbackEnvironment;
  }

  @Override
  @NotNull
  public GitHistoryProvider getVcsHistoryProvider() {
    return myHistoryProvider;
  }

  @Override
  public GitHistoryProvider getVcsBlockHistoryProvider() {
    return myHistoryProvider;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return NAME;
  }

  @Override
  @Nullable
  public UpdateEnvironment createUpdateEnvironment() {
    return myUpdateEnvironment;
  }

  @Override
  @NotNull
  public AnnotationProviderEx getAnnotationProvider() {
    return myAnnotationProvider;
  }

  @Override
  @NotNull
  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  @Override
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return myRevSelector;
  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(@Nullable String revision, @Nullable FilePath path) throws VcsException {
    if (revision == null || revision.length() == 0) return null;
    if (revision.length() > 40) {    // date & revision-id encoded string
      String dateString = revision.substring(0, revision.indexOf("["));
      String rev = revision.substring(revision.indexOf("[") + 1, 40);
      Date d = new Date(Date.parse(dateString));
      return new GitRevisionNumber(rev, d);
    }
    if (path != null) {
      try {
        VirtualFile root = GitUtil.getGitRoot(path);
        return GitRevisionNumber.resolve(myProject, root, revision);
      }
      catch (VcsException e) {
        LOG.info("Unexpected problem with resolving the git revision number: ", e);
        throw e;
      }
    }
    return new GitRevisionNumber(revision);

  }

  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(@Nullable String revision) throws VcsException {
    return parseRevisionNumber(revision, null);
  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return dir.isDirectory() && GitUtil.gitRootOrNull(dir) != null;
  }

  @Override
  protected void start() throws VcsException {
  }

  @Override
  protected void shutdown() throws VcsException {
  }

  @Override
  protected void activate() {
    checkExecutableAndVersion();

    if (myVFSListener == null) {
      myVFSListener = new GitVFSListener(myProject, this, myGit);
    }
    ServiceManager.getService(myProject, VcsUserRegistry.class); // make sure to read the registry before opening commit dialog

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myBranchWidget = new GitBranchWidget(myProject);
      myBranchWidget.activate();
    }
    if (myRepositoryForAnnotationsListener == null) {
      myRepositoryForAnnotationsListener = new GitRepositoryForAnnotationsListener(myProject);
    }
    GitUserRegistry.getInstance(myProject).activate();
  }

  private void checkExecutableAndVersion() {
    boolean executableIsAlreadyCheckedAndFine = false;
    String pathToGit = myAppSettings.getPathToGit();
    if (!pathToGit.contains(File.separator)) { // no path, just sole executable, with a hope that it is in path
      // subject to redetect the path if executable validator fails
      if (!myExecutableValidator.isExecutableValid()) {
        myAppSettings.setPathToGit(new GitExecutableDetector().detect());
      }
      else {
        executableIsAlreadyCheckedAndFine = true; // not to check it twice
      }
    }

    if (executableIsAlreadyCheckedAndFine || myExecutableValidator.checkExecutableAndNotifyIfNeeded()) {
      checkVersion();
    }
  }

  @Override
  protected void deactivate() {
    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }

    if (myBranchWidget != null) {
      myBranchWidget.deactivate();
      myBranchWidget = null;
    }
  }

  @NotNull
  @Override
  public synchronized Configurable getConfigurable() {
    return myConfigurable;
  }

  @Nullable
  public ChangeProvider getChangeProvider() {
    return myChangeProvider;
  }

  /**
   * Show errors as popup and as messages in vcs view.
   *
   * @param list   a list of errors
   * @param action an action
   */
  public void showErrors(@NotNull List<VcsException> list, @NotNull String action) {
    if (list.size() > 0) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("\n");
      buffer.append(GitBundle.message("error.list.title", action));
      for (final VcsException exception : list) {
        buffer.append("\n");
        buffer.append(exception.getMessage());
      }
      final String msg = buffer.toString();
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(myProject, msg, GitBundle.getString("error.dialog.title")));
    }
  }

  /**
   * Shows a plain message in the Version Control Console.
   */
  public void showMessages(@NotNull String message) {
    if (message.length() == 0) return;
    showMessage(message, ConsoleViewContentType.NORMAL_OUTPUT);
  }

  /**
   * Show message in the Version Control Console
   * @param message a message to show
   * @param contentType a style to use
   */
  private void showMessage(@NotNull String message, @NotNull ConsoleViewContentType contentType) {
    if (message.length() > MAX_CONSOLE_OUTPUT_SIZE) {
      message = message.substring(0, MAX_CONSOLE_OUTPUT_SIZE);
    }
    myVcsManager.addMessageToConsoleWindow(message, contentType);
  }

  /**
   * Checks Git version and updates the myVersion variable.
   * In the case of exception or unsupported version reports the problem.
   * Note that unsupported version is also applied - some functionality might not work (we warn about that), but no need to disable at all.
   */
  public void checkVersion() {
    final String executable = myAppSettings.getPathToGit();
    try {
      myVersion = GitVersion.identifyVersion(executable);
      LOG.info("Git version: " + myVersion);
      if (!myVersion.isSupported()) {
        final String SETTINGS_LINK = "settings";
        final String UPDATE_LINK = "update";
        String message = String.format("The <a href='" + SETTINGS_LINK + "'>configured</a> version of Git is not supported: %s.<br/> " +
                                       "The minimal supported version is %s. Please <a href='" + UPDATE_LINK + "'>update</a>.",
                                       myVersion.getPresentation(), GitVersion.MIN.getPresentation());
        VcsNotifier.getInstance(myProject).notifyError("Unsupported Git version", message,
                                                       new NotificationListener.Adapter() {
                                                         @Override
                                                         protected void hyperlinkActivated(@NotNull Notification notification,
                                                                                           @NotNull HyperlinkEvent e) {
                                                           if (SETTINGS_LINK.equals(e.getDescription())) {
                                                             ShowSettingsUtil.getInstance()
                                                               .showSettingsDialog(myProject, getConfigurable().getDisplayName());
                                                           }
                                                           else if (UPDATE_LINK.equals(e.getDescription())) {
                                                             BrowserUtil.browse("http://git-scm.com");
                                                           }
                                                         }
                                                       }
        );
      }
    }
    catch (Exception e) {
      LOG.warn(e);
      if (getExecutableValidator().checkExecutableAndNotifyIfNeeded()) { // check executable before notifying error
        final String reason = (e.getCause() != null ? e.getCause() : e).getMessage();
        String message = GitBundle.message("vcs.unable.to.run.git", executable, reason);
        if (!myProject.isDefault()) {
          showMessage(message, ConsoleViewContentType.SYSTEM_OUTPUT);
        }
        VcsBalloonProblemNotifier.showOverVersionControlView(myProject, message, MessageType.ERROR);
      }
    }
  }

  /**
   * @return the version number of Git, which is used by IDEA. Or {@link GitVersion#NULL} if version info is unavailable yet.
   */
  @NotNull
  public GitVersion getVersion() {
    return myVersion;
  }

  /**
   * Shows a command line message in the Version Control Console
   */
  public void showCommandLine(final String cmdLine) {
    SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss.SSS");
    showMessage(f.format(new Date()) + ": " + cmdLine, ConsoleViewContentType.SYSTEM_OUTPUT);
  }

  /**
   * Shows error message in the Version Control Console
   */
  public void showErrorMessages(final String line) {
    showMessage(line, ConsoleViewContentType.ERROR_OUTPUT);
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  @NotNull
  @Override
  public <S> List<S> filterUniqueRoots(@NotNull List<S> in, @NotNull Function<S, VirtualFile> convertor) {
    Collections.sort(in, comparing(convertor, FilePathComparator.getInstance()));

    for (int i = 1; i < in.size(); i++) {
      final S sChild = in.get(i);
      final VirtualFile child = convertor.apply(sChild);
      final VirtualFile childRoot = GitUtil.gitRootOrNull(child);
      if (childRoot == null) {
        // non-git file actually, skip it
        continue;
      }
      for (int j = i - 1; j >= 0; --j) {
        final S sParent = in.get(j);
        final VirtualFile parent = convertor.apply(sParent);
        // the method check both that parent is an ancestor of the child and that they share common git root
        if (VfsUtilCore.isAncestor(parent, child, false) && VfsUtilCore.isAncestor(childRoot, parent, false)) {
          in.remove(i);
          //noinspection AssignmentToForLoopParameter
          --i;
          break;
        }
      }
    }
    return in;
  }

  @Override
  public RootsConvertor getCustomConvertor() {
    return GitRootConverter.INSTANCE;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsType getType() {
    return VcsType.distributed;
  }

  private final VcsOutgoingChangesProvider<CommittedChangeList> myOutgoingChangesProvider;

  @Override
  protected VcsOutgoingChangesProvider<CommittedChangeList> getOutgoingProviderImpl() {
    return myOutgoingChangesProvider;
  }

  @Override
  public RemoteDifferenceStrategy getRemoteDifferenceStrategy() {
    return RemoteDifferenceStrategy.ASK_TREE_PROVIDER;
  }

  @Override
  public List<CommitExecutor> getCommitExecutors() {
    return myCommitAndPushExecutor != null
           ? Collections.singletonList(myCommitAndPushExecutor)
           : Collections.emptyList();
  }

  @NotNull
  public GitExecutableValidator getExecutableValidator() {
    return myExecutableValidator;
  }

  @Override
  public boolean fileListenerIsSynchronous() {
    return false;
  }

  @Override
  @CalledInAwt
  public void enableIntegration() {
    Runnable task = () -> {
      Collection<VcsRoot> roots = ServiceManager.getService(myProject, VcsRootDetector.class).detect();
      new GitIntegrationEnabler(this, myGit).enable(roots);
    };
    BackgroundTaskUtil.executeOnPooledThread(task, myProject);
  }

  @Override
  public CheckoutProvider getCheckoutProvider() {
    return new GitCheckoutProvider(Git.getInstance());
  }
}
