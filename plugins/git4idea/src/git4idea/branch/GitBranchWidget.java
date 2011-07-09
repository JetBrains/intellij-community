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
package git4idea.branch;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Status bar widget which displays the current branch for the file currently open in the editor.
 * @author Kirill Likhodedov
 */
public class GitBranchWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation, StatusBarWidget.Multiframe,
                                                                  GitRepositoryChangeListener {

  private final GitRepositoryManager myRepositoryManager;
  private volatile String myText = "";
  private volatile String myTooltip = "";

  public GitBranchWidget(Project project) {
    super(project);
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myRepositoryManager.addListenerToAllRepositories(this);
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
  public void repositoryChanged() {
    update();
  }

  @NotNull
  @Override
  public String getText() {
    final String text = myText;
    return StringUtil.isEmpty(text) ? "" : "Git: " + text;
  }

  @NotNull
  @Override
  public String getMaxPossibleText() {
    return "Git: Rebasing abcdefghij";
  }

  @Override
  public float getAlignment() {
    return 0;
  }

  @Override
  public String getTooltipText() {
    return myTooltip;
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
    VirtualFile selectedFile = getSelectedFile();
    if (selectedFile == null) {
      myText = "";
      myTooltip = "";
      return; // no file => just leave previous value
    }

    GitRepository repo = myRepositoryManager.getRepositoryForFile(selectedFile);
    if (repo == null) { // the file is not under version control => display nothing
      myText = "";
      myTooltip = "";
      return;
    }

    updateBranchAndTooltipText(repo);

    myStatusBar.updateWidget(ID());
  }

  @Override
  protected VirtualFile getSelectedFile() {
    final AtomicReference<VirtualFile> selectedFile = new AtomicReference<VirtualFile>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        selectedFile.set(GitBranchWidget.super.getSelectedFile());
      }
    });
    return selectedFile.get();
  }

  private void updateBranchAndTooltipText(@NotNull GitRepository repository) {
    GitRepository.State state = repository.getState();
    if (state == GitRepository.State.DETACHED) {
      String currentRevision = repository.getCurrentRevision();
      assert currentRevision != null : "Current revision can't be null in DETACHED state, only on the fresh repository.";
      myText = currentRevision.substring(0, 7);
      myTooltip = currentRevision;
      return;
    }

    String prefix = "";
    if (state == GitRepository.State.MERGING || state == GitRepository.State.REBASING) {
      prefix = state.toString() + " ";
    }

    GitBranch branch = repository.getCurrentBranch();
    String branchName = (branch == null ? "" : branch.getName());
    myText = prefix + branchName;
    myTooltip = prefix + branchName;
  }

}
