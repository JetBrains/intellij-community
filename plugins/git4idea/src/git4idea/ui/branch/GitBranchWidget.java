/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.ui.branch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Status bar widget which displays the current branch for the file currently open in the editor.
 * @author Kirill Likhodedov
 */
public class GitBranchWidget extends EditorBasedWidget implements StatusBarWidget.MultipleTextValuesPresentation,
                                                                  StatusBarWidget.Multiframe,
                                                                  GitRepositoryChangeListener {
  private static final Logger LOG = Logger.getInstance(GitBranchWidget.class);

  private final GitVcsSettings mySettings;
  private volatile String myText = "";
  private volatile String myTooltip = "";
  private final String myMaxString;

  public GitBranchWidget(Project project) {
    super(project);
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, this);
    mySettings = GitVcsSettings.getInstance(project);
    myMaxString = "Git: Rebasing master";
    updateLater();
  }

  @Override
  public StatusBarWidget copy() {
    return new GitBranchWidget(getProject());
  }

  @NotNull
  @Override
  public String ID() {
    return GitBranchWidget.class.getName();
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    LOG.debug("selection changed");
    update();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    LOG.debug("file opened");
    update();
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    LOG.debug("file closed");
    update();
  }

  @Override
  public void repositoryChanged(@NotNull GitRepository repository) {
    LOG.debug("repository changed");
    updateLater();
  }

  private void updateLater() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        LOG.debug("update after repository change");
        update();
      }
    });
  }

  @Override
  public ListPopup getPopupStep() {
    Project project = getProject();
    if (project == null) {
      return null;
    }
    GitRepository repo = GitBranchUtil.getCurrentRepository(project);
    if (repo == null) {
      return null;
    }
    update(); // update on click
    return GitBranchPopup.getInstance(project, repo).asListPopup();
  }

  @Override
  public String getSelectedValue() {
    final String text = myText;
    return StringUtil.isEmpty(text) ? "" : "Git: " + text;
  }

  @NotNull
  @Override
  @Deprecated
  public String getMaxValue() {
    return myMaxString;
  }

  @Override
  public String getTooltipText() {
    return myTooltip;
  }

  @Override
  // have no effect since the click opens a list popup, and the consumer is not called for the MultipleTextValuesPresentation
  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
          update();
      }
    };
  }

  private void update() {
    Project project = getProject();
    if (project == null || project.isDisposed()) {
      emptyTextAndTooltip();
      return;
    }

    GitRepository repo = GitBranchUtil.getCurrentRepository(project);
    if (repo == null) { // the file is not under version control => display nothing
      emptyTextAndTooltip();
      return;
    }

    int maxLength = myMaxString.length() - 1; // -1, because there are arrows indicating that it is a popup
    myText = StringUtil.shortenTextWithEllipsis(GitBranchUtil.getDisplayableBranchText(repo), maxLength, 5);
    myTooltip = getDisplayableBranchTooltip(repo);
    if (myStatusBar != null) {
      myStatusBar.updateWidget(ID());
    }
    mySettings.setRecentRoot(repo.getRoot().getPath());
  }

  private void emptyTextAndTooltip() {
    myText = "";
    myTooltip = "";
  }

  @NotNull
  private static String getDisplayableBranchTooltip(GitRepository repo) {
    String text = GitBranchUtil.getDisplayableBranchText(repo);
    if (!GitUtil.justOneGitRepository(repo.getProject())) {
      return text + "\n" + "Root: " + repo.getRoot().getName();
    }
    return text;
  }

}
