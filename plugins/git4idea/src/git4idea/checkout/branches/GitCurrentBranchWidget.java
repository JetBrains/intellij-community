/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.checkout.branches;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.vfs.GitReferenceListener;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Status bar widget which displays the current branch.
 * @author Kirill Likhodedov
 */
public class GitCurrentBranchWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation, GitReferenceListener, StatusBarWidget.Multiframe {

  private ProjectLevelVcsManager myVcsManager;
  private AtomicReference<String> myCurrentBranchName = new AtomicReference<String>("");
  private static final Logger LOG = Logger.getInstance(GitCurrentBranchWidget.class.getName());

  public GitCurrentBranchWidget(Project project) {
    super(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  @Override
  public StatusBarWidget copy() {
    return new GitCurrentBranchWidget(getProject());
  }

  @NotNull
  @Override
  public String ID() {
    return "git4idea.GitCurrentBranchWidget";
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    update();
  }

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    update();
  }

  @Override
  public void fileClosed(FileEditorManager source, VirtualFile file) {
    update();
  }

  @Override
  public void referencesChanged(VirtualFile root) {
    update();
  }

  @NotNull
  @Override
  public String getText() {
    final String text = myCurrentBranchName.get();
    return StringUtil.isEmpty(text) ? "" : "Git: " + text;
  }

  @NotNull
  @Override
  public String getMaxPossibleText() {
    return "Git: abcdefghij";
  }

  @Override
  public float getAlignment() {
    return 0;
  }

  @Override
  public String getTooltipText() {
    return "Current Git branch";
  }

  @Override
  // Updates branch information on click
  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
          update();
      }
    };
  }

  private void update() {
    final VirtualFile file = getSelectedFile();
    final Project project = getProject();
    Task.Backgroundable task = new Task.Backgroundable(project, "") {
      @Override
      public void run(ProgressIndicator indicator) {
        if (isDisposed()) return;

        String currentBranchName = null;
        if (file != null) {
          AbstractVcs vcs = myVcsManager.getVcsFor(file);
          if (vcs != null && vcs instanceof GitVcs) {
            VirtualFile root = myVcsManager.getVcsRootFor(file);
            if (root != null) {
              GitBranch currentBranch = null;
              try {
                currentBranch = GitBranch.current(project, root);
              }
              catch (VcsException e) {
                LOG.info("Exception while trying to get current branch for file " + file + " under root " + root, e);
                // doing nothing - null will be set to myCurrentBranchName
              }
              currentBranchName = currentBranch != null ? currentBranch.getName() : null;
            }
          }
        }
        if (currentBranchName == null) {
          currentBranchName = "";
        }
        myCurrentBranchName.set(currentBranchName);
        myStatusBar.updateWidget(ID());
      }
    };
    if (project == null) {
      task.queue();
    } else {
      GitVcs.getInstance(project).runInBackground(task);
    }
  }

}
