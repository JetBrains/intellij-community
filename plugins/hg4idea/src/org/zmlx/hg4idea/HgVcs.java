// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.roots.VcsRootDetector;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.provider.*;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationProvider;
import org.zmlx.hg4idea.provider.commit.HgCheckinEnvironment;
import org.zmlx.hg4idea.provider.commit.HgCloseBranchExecutor;
import org.zmlx.hg4idea.provider.commit.HgCommitAndPushExecutor;
import org.zmlx.hg4idea.provider.commit.HgMQNewExecutor;
import org.zmlx.hg4idea.provider.update.HgUpdateEnvironment;
import org.zmlx.hg4idea.roots.HgIntegrationEnabler;
import org.zmlx.hg4idea.status.HgRemoteStatusUpdater;
import org.zmlx.hg4idea.status.ui.HgHideableWidget;
import org.zmlx.hg4idea.status.ui.HgIncomingOutgoingWidget;
import org.zmlx.hg4idea.status.ui.HgStatusWidget;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.intellij.util.containers.ContainerUtil.exists;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static java.util.Comparator.comparing;

public class HgVcs extends AbstractVcs<CommittedChangeList> {

  public static final Topic<HgUpdater> REMOTE_TOPIC = new Topic<>("hg4idea.remote", HgUpdater.class);
  public static final Topic<HgUpdater> STATUS_TOPIC = new Topic<>("hg4idea.status", HgUpdater.class);
  public static final Topic<HgHideableWidget> INCOMING_OUTGOING_CHECK_TOPIC =
    new Topic<>("hg4idea.incomingcheck", HgHideableWidget.class);
  private static final Logger LOG = Logger.getInstance(HgVcs.class);

  public static final String VCS_NAME = "hg4idea";
  public static final String DISPLAY_NAME = "Mercurial";
  private final static VcsKey ourKey = createKey(VCS_NAME);
  private static final int MAX_CONSOLE_OUTPUT_SIZE = 10000;

  private static final String ORIG_FILE_PATTERN = "*.orig";
  @Nullable public static final String HGENCODING = System.getenv("HGENCODING");

  private final HgChangeProvider changeProvider;
  private final HgRollbackEnvironment rollbackEnvironment;
  private final HgDiffProvider diffProvider;
  private final HgHistoryProvider historyProvider;
  private final HgCheckinEnvironment checkinEnvironment;
  private final HgAnnotationProvider annotationProvider;
  private final HgUpdateEnvironment updateEnvironment;
  private final HgCommittedChangesProvider committedChangesProvider;
  private MessageBusConnection messageBusConnection;
  @NotNull private final HgGlobalSettings globalSettings;
  @NotNull private final HgProjectSettings projectSettings;
  private final ProjectLevelVcsManager myVcsManager;

  private HgVFSListener myVFSListener;
  private final HgMergeProvider myMergeProvider;
  private HgExecutableValidator myExecutableValidator;
  private final Object myExecutableValidatorLock = new Object();
  private File myPromptHooksExtensionFile;
  private final CommitExecutor myCommitAndPushExecutor;
  private final CommitExecutor myMqNewExecutor;
  private final HgCloseBranchExecutor myCloseBranchExecutor;

  private HgRemoteStatusUpdater myHgRemoteStatusUpdater;
  private HgStatusWidget myStatusWidget;
  private HgIncomingOutgoingWidget myIncomingWidget;
  private HgIncomingOutgoingWidget myOutgoingWidget;
  @NotNull private HgVersion myVersion = HgVersion.NULL;  // version of Hg which this plugin uses.

  public HgVcs(@NotNull Project project,
               @NotNull HgGlobalSettings globalSettings,
               @NotNull HgProjectSettings projectSettings,
               ProjectLevelVcsManager vcsManager) {
    super(project, VCS_NAME);
    this.globalSettings = globalSettings;
    this.projectSettings = projectSettings;
    myVcsManager = vcsManager;
    changeProvider = new HgChangeProvider(project, getKeyInstanceMethod());
    rollbackEnvironment = new HgRollbackEnvironment(project);
    diffProvider = new HgDiffProvider(project);
    historyProvider = new HgHistoryProvider(project);
    checkinEnvironment = new HgCheckinEnvironment(project);
    annotationProvider = new HgAnnotationProvider(project);
    updateEnvironment = new HgUpdateEnvironment(project);
    committedChangesProvider = new HgCommittedChangesProvider(project, this);
    myMergeProvider = new HgMergeProvider(myProject);
    myCommitAndPushExecutor = new HgCommitAndPushExecutor(checkinEnvironment);
    myMqNewExecutor = new HgMQNewExecutor(checkinEnvironment);
    myCloseBranchExecutor = new HgCloseBranchExecutor(checkinEnvironment);
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public Configurable getConfigurable() {
    return new HgProjectConfigurable(myProject, globalSettings, projectSettings);
  }

  @NotNull
  public HgProjectSettings getProjectSettings() {
    return projectSettings;
  }

  @Override
  public ChangeProvider getChangeProvider() {
    return changeProvider;
  }

  @Nullable
  @Override
  public RollbackEnvironment createRollbackEnvironment() {
    return rollbackEnvironment;
  }

  @Override
  public DiffProvider getDiffProvider() {
    return diffProvider;
  }

  @Override
  public VcsHistoryProvider getVcsHistoryProvider() {
    return historyProvider;
  }

  @Override
  public VcsHistoryProvider getVcsBlockHistoryProvider() {
    return getVcsHistoryProvider();
  }

  @Nullable
  @Override
  public CheckinEnvironment createCheckinEnvironment() {
    return checkinEnvironment;
  }

  @Override
  public AnnotationProvider getAnnotationProvider() {
    return annotationProvider;
  }

  @Override
  public MergeProvider getMergeProvider() {
    return myMergeProvider;
  }

  @Nullable
  @Override
  public UpdateEnvironment createUpdateEnvironment() {
    return updateEnvironment;
  }

  @Override
  public UpdateEnvironment getIntegrateEnvironment() {
    return null;
  }

  @Override
  public boolean fileListenerIsSynchronous() {
    return false;
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return committedChangesProvider;
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
      final VirtualFile childRoot = HgUtil.getHgRootOrNull(myProject, child);
      if (childRoot == null) {
        continue;
      }
      for (int j = i - 1; j >= 0; --j) {
        final S sParent = in.get(j);
        final VirtualFile parent = convertor.apply(sParent);
        // if the parent is an ancestor of the child and that they share common root, the child is removed
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
    return HgRootsHandler.getInstance(myProject);
  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return HgUtil.getNearestHgRoot(dir) != null;
  }

  /**
   * @return the prompthooks.py extension used for capturing prompts from Mercurial and requesting IDEA's user about authentication.
   */
  @NotNull
  public File getPromptHooksExtensionFile() {
    if (myPromptHooksExtensionFile == null || !myPromptHooksExtensionFile.exists()) {
      // check that hooks are available
      myPromptHooksExtensionFile = HgUtil.getTemporaryPythonFile("prompthooks");
      if (myPromptHooksExtensionFile == null || !myPromptHooksExtensionFile.exists()) {
        LOG.error(
          "prompthooks.py Mercurial extension is not found. Please reinstall " + ApplicationNamesInfo.getInstance().getProductName());
      }
    }
    return myPromptHooksExtensionFile;
  }

  @Override
  public void activate() {
    // validate hg executable on start and update hg version
    checkExecutableAndVersion();

    // status bar
    myStatusWidget = new HgStatusWidget(this, getProject(), projectSettings);
    myStatusWidget.activate();

    myIncomingWidget = new HgIncomingOutgoingWidget(this, getProject(), projectSettings, true);
    myOutgoingWidget = new HgIncomingOutgoingWidget(this, getProject(), projectSettings, false);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myIncomingWidget.activate();
      myOutgoingWidget.activate();
    }, ModalityState.NON_MODAL);

    // updaters and listeners
    myHgRemoteStatusUpdater =
      new HgRemoteStatusUpdater(this, myIncomingWidget.getChangesetStatus(), myOutgoingWidget.getChangesetStatus(),
                                projectSettings);
    myHgRemoteStatusUpdater.activate();

    messageBusConnection = myProject.getMessageBus().connect();
    myVFSListener = new HgVFSListener(myProject, this);

    // ignore temporary files
    final String ignoredPattern = FileTypeManager.getInstance().getIgnoredFilesList();
    if (!ignoredPattern.contains(ORIG_FILE_PATTERN)) {
      final String newPattern = ignoredPattern + (ignoredPattern.endsWith(";") ? "" : ";") + ORIG_FILE_PATTERN;
      HgUtil.runWriteActionLater(() -> FileTypeManager.getInstance().setIgnoredFilesList(newPattern));
    }
  }

  private void checkExecutableAndVersion() {
    if (!ApplicationManager.getApplication().isUnitTestMode() && getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
      checkVersion();
    }
  }

  @Override
  public void deactivate() {
    if (myHgRemoteStatusUpdater != null) {
      myHgRemoteStatusUpdater.deactivate();
      myHgRemoteStatusUpdater = null;
    }
    if (myStatusWidget != null) {
      myStatusWidget.deactivate();
      myStatusWidget = null;
    }
    if (myIncomingWidget != null) {
      myIncomingWidget.deactivate();
      myIncomingWidget = null;
    }
    if (myOutgoingWidget != null) {
      myOutgoingWidget.deactivate();
      myOutgoingWidget = null;
    }
    if (messageBusConnection != null) {
      messageBusConnection.disconnect();
    }

    if (myVFSListener != null) {
      Disposer.dispose(myVFSListener);
      myVFSListener = null;
    }

    super.deactivate();
  }

  @Nullable
  public static HgVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcsManager == null) {
      return null;
    }
    return (HgVcs)vcsManager.findVcsByName(VCS_NAME);
  }

  @NotNull
  public HgGlobalSettings getGlobalSettings() {
    return globalSettings;
  }

  public void showMessageInConsole(@NotNull String message, @NotNull ConsoleViewContentType contentType) {
    if (message.length() > MAX_CONSOLE_OUTPUT_SIZE) {
      message = message.substring(0, MAX_CONSOLE_OUTPUT_SIZE);
    }
    myVcsManager.addMessageToConsoleWindow(message, contentType);
  }

  public HgExecutableValidator getExecutableValidator() {
    synchronized (myExecutableValidatorLock) {
      if (myExecutableValidator == null) {
        myExecutableValidator = new HgExecutableValidator(myProject);
      }
      return myExecutableValidator;
    }
  }

  @Override
  public List<CommitExecutor> getCommitExecutors() {
    ArrayList<CommitExecutor> commitExecutors = newArrayList(myCommitAndPushExecutor);
    if (exists(HgUtil.getRepositoryManager(myProject).getRepositories(), r -> r.getRepositoryConfig().isMqUsed())) {
      commitExecutors.add(myMqNewExecutor);
    }
    return commitExecutors;
  }

  @NotNull
  public HgCloseBranchExecutor getCloseBranchExecutor() {
    return myCloseBranchExecutor;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsType getType() {
    return VcsType.distributed;
  }

  @Override
  @CalledInAwt
  public void enableIntegration() {
    BackgroundTaskUtil.executeOnPooledThread(myProject, () -> {
      Collection<VcsRoot> roots = ServiceManager.getService(myProject, VcsRootDetector.class).detect();
      new HgIntegrationEnabler(this).enable(roots);
    });
  }

  @Override
  public CheckoutProvider getCheckoutProvider() {
    return new HgCheckoutProvider();
  }

  /**
   * Checks Hg version and updates the myVersion variable.
   * In the case of nullable or unsupported version reports the problem.
   */
  public void checkVersion() {
    final String executable = HgExecutableManager.getInstance().getHgExecutable(myProject);
    VcsNotifier vcsNotifier = VcsNotifier.getInstance(myProject);
    final String SETTINGS_LINK = "settings";
    final String UPDATE_LINK = "update";
    NotificationListener linkAdapter = new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification,
                                        @NotNull HyperlinkEvent e) {
        if (SETTINGS_LINK.equals(e.getDescription())) {
          ShowSettingsUtil.getInstance()
            .showSettingsDialog(myProject, getConfigurable().getDisplayName());
        }
        else if (UPDATE_LINK.equals(e.getDescription())) {
          BrowserUtil.browse("http://mercurial.selenic.com");
        }
      }
    };
    try {
      myVersion = HgVersion.identifyVersion(executable);
      //if version is not supported, but have valid hg executable
      if (!myVersion.isSupported()) {
        LOG.info("Unsupported Hg version: " + myVersion);
        String message = String.format("The <a href='" + SETTINGS_LINK + "'>configured</a> version of Hg is not supported: %s.<br/> " +
                                       "The minimal supported version is %s. Please <a href='" + UPDATE_LINK + "'>update</a>.",
                                       myVersion, HgVersion.MIN);
        vcsNotifier.notifyError("Unsupported Hg version", message, linkAdapter);
      }
      else if (myVersion.hasUnsupportedExtensions()) {
        String unsupportedExtensionsAsString = myVersion.getUnsupportedExtensions().toString();
        LOG.warn("Unsupported Hg extensions: " + unsupportedExtensionsAsString);
        String message = String.format("Some hg extensions %s are not found or not supported by your hg version and will be ignored.\n" +
                                       "Please, update your hgrc or Mercurial.ini file", unsupportedExtensionsAsString);
        vcsNotifier.notifyWarning("Unsupported Hg version", message);
      }
    }
    catch (Exception e) {
      if (getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
        //sometimes not hg application has version command, but we couldn't parse an answer as valid hg,
        // so parse(output) throw ParseException, but hg and git executable seems to be valid in this case
        final String reason = (e.getCause() != null ? e.getCause() : e).getMessage();
        String message = HgVcsMessages.message("hg4idea.unable.to.run.hg", executable);
        vcsNotifier.notifyError(message,
                                reason +
                                "<br/> Please check your hg executable path in <a href='" +
                                SETTINGS_LINK +
                                "'> settings </a>",
                                linkAdapter
        );
      }
    }
  }

  /**
   * @return the version number of Hg, which is used by IDEA. Or {@link HgVersion#NULL} if version info is unavailable.
   */
  @NotNull
  public HgVersion getVersion() {
    return myVersion;
  }
}
