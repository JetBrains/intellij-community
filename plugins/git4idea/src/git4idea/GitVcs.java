package git4idea;
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
 * Authors: gevession, Erlend Simonsen & Mark Scott
 *
 * This code was originally derived from the MKS & Mercurial IDEA VCS plugins
 */

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.UpdateEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.annotate.GitAnnotationProvider;
import git4idea.changes.GitChangeProvider;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitVcsConfigurable;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersion;
import git4idea.diff.GitDiffProvider;
import git4idea.history.GitHistoryProvider;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitMergeProvider;
import git4idea.rollback.GitRollbackEnvironment;
import git4idea.update.GitUpdateEnvironment;
import git4idea.vfs.GitVFSListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Git VCS implementation
 */
public class GitVcs extends AbstractVcs {
  /**
   * Vcs name
   */
  @NonNls public static final String NAME = "Git";
  /**
   * change provider
   */
  private final ChangeProvider myChangeProvider;
  /**
   * commit support
   */
  private final CheckinEnvironment myCheckinEnvironment;
  /**
   * rollback support
   */
  private final RollbackEnvironment myRollbackEnvironment;
  /**
   * update support
   */
  private final GitUpdateEnvironment myUpdateEnvironment;
  /**
   * annotate file support
   */
  private final GitAnnotationProvider myAnnotationProvider;
  /**
   * diff provider
   */
  private final DiffProvider myDiffProvider;
  /**
   * history provider
   */
  private final VcsHistoryProvider myHistoryProvider;
  /**
   * cached instace of vcs manager for the project
   */
  private final ProjectLevelVcsManager myVcsManager;
  /**
   * project vcs settings
   */
  private final GitVcsSettings mySettings;
  /**
   * cached instace of color scheme
   */
  private final EditorColorsScheme myEditorColorsScheme;
  /**
   * configuration support
   */
  private final Configurable myConfigurable;
  /**
   * selector for revisions
   */
  private final RevisionSelector myRevSelector;
  /**
   * merge provider
   */
  private final GitMergeProvider myMergeProvider;
  /**
   * a vfs listener
   */
  private GitVFSListener myVFSListener;
  /**
   * If true, the git version has been checked
   */
  private boolean myVersionChecked;
  /**
   * Checking the version lock (used to prevent infinite recusion)
   */
  private AtomicBoolean myCheckingVersion = new AtomicBoolean();
  /**
   * The path to executable at the time of version check
   */
  private String myVersionCheckExcecutable = "";


  public static GitVcs getInstance(@NotNull Project project) {
    return (GitVcs)ProjectLevelVcsManager.getInstance(project).findVcsByName(NAME);
  }

  public GitVcs(@NotNull Project project,
                @NotNull final GitChangeProvider gitChangeProvider,
                @NotNull final GitCheckinEnvironment gitCheckinEnvironment,
                @NotNull final ProjectLevelVcsManager gitVcsManager,
                @NotNull final GitAnnotationProvider gitAnnotationProvider,
                @NotNull final GitDiffProvider gitDiffProvider,
                @NotNull final GitHistoryProvider gitHistoryProvider,
                @NotNull final GitRollbackEnvironment gitRollbackEnvironment,
                @NotNull final GitVcsSettings gitSettings) {
    super(project);

    myVcsManager = gitVcsManager;
    mySettings = gitSettings;
    myChangeProvider = gitChangeProvider;
    myCheckinEnvironment = gitCheckinEnvironment;
    myAnnotationProvider = gitAnnotationProvider;
    myDiffProvider = gitDiffProvider;
    myEditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    myHistoryProvider = gitHistoryProvider;
    myRollbackEnvironment = gitRollbackEnvironment;
    myRevSelector = new GitRevisionSelector();
    myConfigurable = new GitVcsConfigurable(mySettings, myProject);
    myUpdateEnvironment = new GitUpdateEnvironment(myProject, mySettings, myConfigurable);
    myMergeProvider = new GitMergeProvider(myProject);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String getName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public CheckinEnvironment getCheckinEnvironment() {
    return myCheckinEnvironment;
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public MergeProvider getMergeProvider() {
    return myMergeProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public RollbackEnvironment getRollbackEnvironment() {
    return myRollbackEnvironment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public VcsHistoryProvider getVcsHistoryProvider() {
    return myHistoryProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public String getDisplayName() {
    return NAME;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public UpdateEnvironment getUpdateEnvironment() {
    return myUpdateEnvironment;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public UpdateEnvironment getStatusEnvironment() {
    return getUpdateEnvironment();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public GitAnnotationProvider getAnnotationProvider() {
    return myAnnotationProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  public DiffProvider getDiffProvider() {
    return myDiffProvider;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @Nullable
  public RevisionSelector getRevisionSelector() {
    return myRevSelector;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UpdateEnvironment getIntegrateEnvironment() {
    return getUpdateEnvironment();
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings({"deprecation"})
  @Override
  @Nullable
  public VcsRevisionNumber parseRevisionNumber(String revision) {
    if (revision == null || revision.length() == 0) return null;

    if (revision.length() > 40) {    // date & revision-id encoded string
      String datestr = revision.substring(0, revision.indexOf("["));
      String rev = revision.substring(revision.indexOf("[") + 1, 40);
      Date d = new Date(Date.parse(datestr));
      return new GitRevisionNumber(rev, d);
    }

    return new GitRevisionNumber(revision);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isVersionedDirectory(VirtualFile dir) {
    return dir.isDirectory() && GitUtil.gitRootOrNull(dir) != null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void activate() {
    super.activate();
    if (myVFSListener == null) {
      myVFSListener = new GitVFSListener(myProject, this);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deactivate() {
    if (myVFSListener != null) {
      myVFSListener.dispose();
      myVFSListener = null;
    }
    super.deactivate();
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public synchronized Configurable getConfigurable() {
    return myConfigurable;
  }

  /**
   * {@inheritDoc}
   */
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
      StringBuffer buffer = new StringBuffer();
      buffer.append("\n");
      buffer.append(GitBundle.message("error.list.title", action));
      for (final VcsException exception : list) {
        buffer.append("\n");
        buffer.append(exception.getMessage());
      }
      String msg = buffer.toString();
      showMessage(msg, CodeInsightColors.ERRORS_ATTRIBUTES);
      Messages.showErrorDialog(myProject, msg, GitBundle.getString("error.dialog.title"));
    }
  }

  /**
   * Show a plain message in vcs view
   *
   * @param message a message to show
   */
  public void showMessages(@NotNull String message) {
    if (message.length() == 0) return;
    showMessage(message, HighlighterColors.TEXT);
  }

  /**
   * @return vcs settings for the current project
   */
  @NotNull
  public GitVcsSettings getSettings() {
    return mySettings;
  }

  /**
   * Show message in the VCS view
   *
   * @param message a message to show
   * @param style   a style to use
   */
  private void showMessage(@NotNull String message, final TextAttributesKey style) {
    myVcsManager.addMessageToConsoleWindow(message, myEditorColorsScheme.getAttributes(style));
  }

  /**
   * Check version and report problem
   */
  public void checkVersion() {
    if (!myCheckingVersion.compareAndSet(false, true)) {
      return;
    }
    try {
      if (myProject.isDefault()) {
        return;
      }
      final String executable = mySettings.GIT_EXECUTABLE;
      if (myVersionChecked && myVersionCheckExcecutable.equals(executable)) {
        return;
      }
      myVersionChecked = true;
      myVersionCheckExcecutable = executable;
      final String version;
      try {
        version = version(myProject).trim();
      }
      catch (VcsException e) {
        String reason = (e.getCause() != null ? e.getCause() : e).getMessage();
        showMessage(GitBundle.message("vcs.unable.to.run.git", executable, reason), CodeInsightColors.ERRORS_ATTRIBUTES);
        return;
      }
      if (!GitVersion.parse(version).isSupported()) {
        showMessage(GitBundle.message("vcs.unsupported.version", version, GitVersion.MIN), CodeInsightColors.WARNINGS_ATTRIBUTES);
      }
    }
    finally {
      myCheckingVersion.set(false);
    }
  }

  /**
   * Get the version of configured git
   *
   * @param project the project
   * @return a version of configured git
   * @throws com.intellij.openapi.vcs.VcsException
   *          an error if there is a problem with running git
   */
  public static String version(Project project) throws VcsException {
    final String s;
    GitSimpleHandler h = new GitSimpleHandler(project, new File("."), "version");
    h.setNoSSH(true);
    h.setSilent(true);
    s = h.run();
    return s;
  }
}