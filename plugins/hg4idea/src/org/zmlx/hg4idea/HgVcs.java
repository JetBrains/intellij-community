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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
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
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.provider.*;
import org.zmlx.hg4idea.provider.annotate.HgAnnotationProvider;
import org.zmlx.hg4idea.provider.commit.HgCheckinEnvironment;
import org.zmlx.hg4idea.provider.commit.HgCommitAndPushExecutor;
import org.zmlx.hg4idea.provider.commit.HgMQNewExecutor;
import org.zmlx.hg4idea.provider.update.HgUpdateEnvironment;
import org.zmlx.hg4idea.roots.HgIntegrationEnabler;
import org.zmlx.hg4idea.status.HgRemoteStatusUpdater;
import org.zmlx.hg4idea.status.ui.HgWidgetUpdater;
import org.zmlx.hg4idea.util.HgUtil;
import org.zmlx.hg4idea.util.HgVersion;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.intellij.platform.util.coroutines.CoroutineScopeKt.childScope;
import static com.intellij.util.concurrency.AppJavaExecutorUtil.awaitCancellationAndDispose;
import static com.intellij.util.containers.ContainerUtil.exists;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.*;

public final class HgVcs extends AbstractVcs {
  @Topic.ProjectLevel
  public static final Topic<HgUpdater> REMOTE_TOPIC = new Topic<>("hg4idea.remote", HgUpdater.class);

  @Topic.ProjectLevel
  public static final Topic<HgUpdater> STATUS_TOPIC = new Topic<>("hg4idea.status", HgUpdater.class);

  @Topic.ProjectLevel
  public static final Topic<HgWidgetUpdater> INCOMING_OUTGOING_CHECK_TOPIC = new Topic<>("hg4idea.incomingcheck", HgWidgetUpdater.class);

  private static final Logger LOG = Logger.getInstance(HgVcs.class);

  public static final @NonNls String VCS_NAME = "hg4idea";
  public static final Supplier<@Nls String> DISPLAY_NAME = HgBundle.messagePointer("hg4idea.vcs.name");
  public static final Supplier<@Nls String> SHORT_DISPLAY_NAME = HgBundle.messagePointer("hg4idea.vcs.short.name");
  private static final VcsKey ourKey = createKey(VCS_NAME);
  private static final int MAX_CONSOLE_OUTPUT_SIZE = 10000;

  private static final @NonNls String ORIG_FILE_PATTERN = "*.orig";
  public static final @Nullable @NonNls String HGENCODING = System.getenv("HGENCODING");

  private final HgChangeProvider changeProvider;
  private final HgRollbackEnvironment rollbackEnvironment;
  private final HgDiffProvider diffProvider;
  private final HgHistoryProvider historyProvider;
  private final HgCheckinEnvironment checkinEnvironment;
  private final HgAnnotationProvider annotationProvider;
  private final HgUpdateEnvironment updateEnvironment;
  private final HgCommittedChangesProvider committedChangesProvider;

  private final AtomicReference<CoroutineScope> myActiveScope = new AtomicReference<>();

  private final HgMergeProvider myMergeProvider;
  private HgExecutableValidator myExecutableValidator;
  private final Object myExecutableValidatorLock = new Object();
  private File myPromptHooksExtensionFile;
  private final CommitExecutor myCommitAndPushExecutor;
  private final CommitExecutor myMqNewExecutor;

  private HgRemoteStatusUpdater myHgRemoteStatusUpdater;
  private @NotNull HgVersion myVersion = HgVersion.NULL;  // version of Hg which this plugin uses.

  public HgVcs(@NotNull Project project) {
    super(project, VCS_NAME);

    changeProvider = new HgChangeProvider(project, getKeyInstanceMethod());
    rollbackEnvironment = new HgRollbackEnvironment(project);
    diffProvider = new HgDiffProvider(project);
    historyProvider = new HgHistoryProvider(project);
    checkinEnvironment = new HgCheckinEnvironment(this);
    annotationProvider = new HgAnnotationProvider(project);
    updateEnvironment = new HgUpdateEnvironment(project);
    committedChangesProvider = new HgCommittedChangesProvider(project, this);
    myMergeProvider = new HgMergeProvider(myProject);
    myCommitAndPushExecutor = new HgCommitAndPushExecutor();
    myMqNewExecutor = new HgMQNewExecutor();
  }

  @Override
  public @NotNull String getDisplayName() {
    return DISPLAY_NAME.get();
  }

  @Override
  public @NotNull String getShortName() {
    return SHORT_DISPLAY_NAME.get();
  }

  @Override
  public @Nls @NotNull String getShortNameWithMnemonic() {
    return HgBundle.message("hg4idea.vcs.short.name.with.mnemonic");
  }

  public @NotNull HgProjectSettings getProjectSettings() {
    return HgProjectSettings.getInstance(myProject);
  }

  @Override
  public ChangeProvider getChangeProvider() {
    return changeProvider;
  }

  @Override
  public @Nullable RollbackEnvironment createRollbackEnvironment() {
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

  @Override
  public @Nullable CheckinEnvironment createCheckinEnvironment() {
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

  @Override
  public @Nullable UpdateEnvironment createUpdateEnvironment() {
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
  public FileStatus[] getProvidedStatuses() {
    return new FileStatus[]{
      HgChangeProvider.FileStatuses.COPIED,
      HgChangeProvider.FileStatuses.RENAMED
    };
  }

  @Override
  public CommittedChangesProvider getCommittedChangesProvider() {
    return committedChangesProvider;
  }

  @Override
  public boolean allowsNestedRoots() {
    return true;
  }

  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return HgUtil.getNearestHgRoot(dir) != null;
  }

  /**
   * @return the prompthooks.py extension used for capturing prompts from Mercurial and requesting IDEA's user about authentication.
   */
  public @NotNull File getPromptHooksExtensionFile() {
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
    CoroutineScope globalScope = HgDisposable.getCoroutineScope(myProject);
    CoroutineScope activeScope = childScope(globalScope, "HgVcs", EmptyCoroutineContext.INSTANCE, true);

    Disposable disposable = Disposer.newDisposable();
    awaitCancellationAndDispose(activeScope, disposable);

    // workaround the race between 'activate' and 'deactivate'
    CoroutineScope oldScope = myActiveScope.getAndSet(activeScope);
    if (oldScope != null) CoroutineScopeKt.cancel(oldScope, null);

    // validate hg executable on start and update hg version
    checkExecutableAndVersion();

    // updaters and listeners
    HgRemoteStatusUpdater remoteStatusUpdater = new HgRemoteStatusUpdater(this);
    Disposer.register(disposable, remoteStatusUpdater);
    myHgRemoteStatusUpdater = remoteStatusUpdater;

    HgVFSListener VFSListener = HgVFSListener.createInstance(this, activeScope);
    Disposer.register(disposable, VFSListener);

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
    CoroutineScope oldScope = myActiveScope.getAndSet(null);
    if (oldScope != null) CoroutineScopeKt.cancel(oldScope, null);
  }

  public static @Nullable HgVcs getInstance(Project project) {
    if (project == null || project.isDisposed()) {
      return null;
    }
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    if (vcsManager == null) {
      return null;
    }
    return (HgVcs)vcsManager.findVcsByName(VCS_NAME);
  }

  public void showMessageInConsole(@NotNull @Nls String message, @NotNull ConsoleViewContentType contentType) {
    if (message.length() > MAX_CONSOLE_OUTPUT_SIZE) {
      message = message.substring(0, MAX_CONSOLE_OUTPUT_SIZE);
    }
    ProjectLevelVcsManager.getInstance(myProject).addMessageToConsoleWindow(message, contentType);
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
    if (exists(HgUtil.getRepositoryManager(myProject).getRepositories(), r -> r.getRepositoryConfig().isMqUsed())) {
      return List.of(myCommitAndPushExecutor, myMqNewExecutor);
    }
    return List.of(myCommitAndPushExecutor);
  }

  public @Nullable HgRemoteStatusUpdater getRemoteStatusUpdater() {
    return myHgRemoteStatusUpdater;
  }

  public static VcsKey getKey() {
    return ourKey;
  }

  @Override
  public VcsType getType() {
    return VcsType.distributed;
  }

  @Override
  @RequiresEdt
  public void enableIntegration(@Nullable VirtualFile targetDirectory) {
    new Task.Backgroundable(myProject, HgBundle.message("progress.title.enabling.hg"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        new HgIntegrationEnabler(HgVcs.this, targetDirectory).detectAndEnable();
      }
    }.queue();
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
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, HgProjectConfigurable.class);
        }
        else if (UPDATE_LINK.equals(e.getDescription())) {
          BrowserUtil.browse("http://mercurial.selenic.com");
        }
      }
    };
    try {
      myVersion = HgVersion.identifyVersion(myProject, executable);
      //if version is not supported, but have valid hg executable
      if (!myVersion.isSupported()) {
        LOG.info("Unsupported Hg version: " + myVersion);
        String message = HgBundle.message("hg4idea.version.update", SETTINGS_LINK, myVersion, HgVersion.MIN, UPDATE_LINK);
        vcsNotifier.notifyError(UNSUPPORTED_VERSION, HgBundle.message("hg4idea.version.unsupported"), message, linkAdapter);
      }
      else if (myVersion.hasUnsupportedExtensions()) {
        String unsupportedExtensionsAsString = myVersion.getUnsupportedExtensions().toString();
        LOG.warn("Unsupported Hg extensions: " + unsupportedExtensionsAsString);
        String message = HgBundle.message("hg4idea.version.unsupported.ext", unsupportedExtensionsAsString);
        vcsNotifier.notifyWarning(UNSUPPORTED_EXT, HgBundle.message("hg4idea.version.unsupported"), message);
      }
    }
    catch (Exception e) {
      if (getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
        //sometimes not hg application has version command, but we couldn't parse an answer as valid hg,
        // so parse(output) throw ParseException, but hg and git executable seems to be valid in this case
        final String reason = (e.getCause() != null ? e.getCause() : e).getMessage();
        String message = HgBundle.message("hg4idea.unable.to.run.hg", executable);
        vcsNotifier.notifyError(UNABLE_TO_RUN_EXEC, message,
                                HgBundle.message("hg4idea.exec.not.found", reason, SETTINGS_LINK),
                                linkAdapter
        );
      }
    }
  }

  /**
   * @return the version number of Hg, which is used by IDEA. Or {@link HgVersion#NULL} if version info is unavailable.
   */
  public @NotNull HgVersion getVersion() {
    return myVersion;
  }
}
