// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.dvcs.ui.DvcsStatusWidget;
import com.intellij.ide.DataManager;
import com.intellij.ide.navigationToolbar.experimental.ExperimentalToolbarStateListener;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.ExperimentalUI;
import git4idea.GitBranchesUsageCollector;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchIncomingOutgoingManager;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A status bar widget which displays the current branch for a file currently open in the editor.
 */
public class GitBranchWidget extends DvcsStatusWidget<GitRepository> {
  private static final @NonNls String ID = "git";
  private final GitVcsSettings mySettings;

  public GitBranchWidget(@NotNull Project project) {
    super(project, GitVcs.DISPLAY_NAME.get());
    mySettings = GitVcsSettings.getInstance(project);

    project.getMessageBus().connect(this).subscribe(GitRepository.GIT_REPO_CHANGE, r -> updateLater());
    project.getMessageBus().connect(this).subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, this::updateLater);
  }

  @Override
  public @NotNull String ID() {
    return ID;
  }

  @Override
  public StatusBarWidget copy() {
    return new GitBranchWidget(getProject());
  }

  @Override
  protected @Nullable GitRepository guessCurrentRepository(@NotNull Project project, @Nullable VirtualFile selectedFile) {
    return GitBranchUtil.guessWidgetRepository(project, selectedFile);
  }

  @Override
  protected @NotNull Icon getIcon(@NotNull GitRepository repository) {
    return BranchIconUtil.Companion.getBranchIcon(repository);
  }

  @Override
  protected @NotNull String getFullBranchName(@NotNull GitRepository repository) {
    return GitBranchUtil.getDisplayableBranchText(repository);
  }

  @Override
  protected boolean isMultiRoot(@NotNull Project project) {
    return !GitUtil.justOneGitRepository(project);
  }

  @Override
  protected @NotNull JBPopup getWidgetPopup(@NotNull Project project, @NotNull GitRepository repository) {
    GitBranchesUsageCollector.branchWidgetClicked();
    if (GitBranchesTreePopup.isEnabled()) {
      return GitBranchesTreePopup.create(project);
    }
    else {
      return GitBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(myStatusBar.getComponent()))
        .asListPopup();
    }
  }

  @Override
  protected void rememberRecentRoot(@NotNull String path) {
    mySettings.setRecentRoot(path);
  }

  @Override
  protected @NlsContexts.Tooltip @Nullable String getToolTip(@Nullable GitRepository repository) {
    if (repository != null && repository.getState() == Repository.State.DETACHED) {
      return GitBundle.message("git.status.bar.widget.tooltip.detached");
    }
    return super.getToolTip(repository);
  }

  static final class Listener implements VcsRepositoryMappingListener {
    private final Project myProject;

    Listener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void mappingChanged() {
      myProject.getService(StatusBarWidgetsManager.class).updateWidget(Factory.class);
    }
  }

  static final class Factory implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
      return GitBundle.message("git.status.bar.widget.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
      return (isEnabledByDefault() || ExperimentalUI.isNewUI()) && !GitRepositoryManager.getInstance(project).getRepositories().isEmpty();
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      return new GitBranchWidget(project);
    }

    @Override
    public boolean isEnabledByDefault() {
      return !ExperimentalUI.isNewUI() && // Disabled by default in ExperimentalUI per designers request.
             (!ToolbarSettings.getInstance().isVisible() || !ToolbarSettings.getInstance().isAvailable());
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
      Disposer.dispose(widget);
    }

    @Override
    public boolean canBeEnabledOn(@NotNull StatusBar statusBar) {
      return true;
    }
  }

  static final class MyExperimentalToolbarStateListener implements ExperimentalToolbarStateListener {
    private final Project myProject;

    MyExperimentalToolbarStateListener(Project project) {
      myProject = project;
    }

    @Override
    public void refreshVisibility() {
      myProject.getService(StatusBarWidgetsManager.class).updateWidget(Factory.class);
    }
  }
}
