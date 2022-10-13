// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.status.ui;

import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.dvcs.ui.DvcsStatusWidget;
import com.intellij.ide.DataManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgProjectSettings;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.branch.HgBranchPopup;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Objects;

/**
 * Widget to display basic hg status in the status bar.
 */
public class HgStatusWidget extends DvcsStatusWidget<HgRepository> {
  private static final @NonNls String ID = "hg";

  private final @NotNull HgVcs myVcs;
  private final @NotNull HgProjectSettings myProjectSettings;

  public HgStatusWidget(@NotNull HgVcs vcs, @NotNull Project project, @NotNull HgProjectSettings projectSettings) {
    super(project, vcs.getShortName());
    myVcs = vcs;
    myProjectSettings = projectSettings;

    project.getMessageBus().connect(this).subscribe(HgVcs.STATUS_TOPIC, (p, root) -> updateLater());
  }

  @Override
  public @NotNull String ID() {
    return ID;
  }

  @Override
  public StatusBarWidget copy() {
    return new HgStatusWidget(myVcs, myProject, myProjectSettings);
  }

  @Override
  @CalledInAny
  protected @Nullable HgRepository guessCurrentRepository(@NotNull Project project, @Nullable VirtualFile selectedFile) {
    return HgUtil.guessWidgetRepository(project, selectedFile);
  }

  @Override
  protected @NotNull String getFullBranchName(@NotNull HgRepository repository) {
    return HgUtil.getDisplayableBranchOrBookmarkText(repository);
  }

  @Override
  protected boolean isMultiRoot(@NotNull Project project) {
    return HgUtil.getRepositoryManager(project).moreThanOneRoot();
  }

  @Override
  protected @Nullable JBPopup getWidgetPopup(@NotNull Project project, @NotNull HgRepository repository) {
    return HgBranchPopup.getInstance(project, repository, DataManager.getInstance().getDataContext(myStatusBar.getComponent())).asListPopup();
  }

  @Override
  protected void rememberRecentRoot(@NotNull String path) {
    myProjectSettings.setRecentRootPath(path);
  }

  public static class Listener implements VcsRepositoryMappingListener {
    private final Project myProject;

    public Listener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void mappingChanged() {
      myProject.getService(StatusBarWidgetsManager.class).updateWidget(Factory.class);
    }
  }

  public static class Factory implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
      return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
      return HgBundle.message("hg4idea.status.bar.widget.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
      return !project.getService(HgRepositoryManager.class).getRepositories().isEmpty();
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
      return new HgStatusWidget(Objects.requireNonNull(HgVcs.getInstance(project)), project, HgProjectSettings.getInstance(project));
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
}
