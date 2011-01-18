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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedWidget;
import com.intellij.util.Consumer;
import git4idea.GitBranch;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

/**
 * Status bar widget which displays the current branch for the file currently open in the editor.
 * @author Kirill Likhodedov
 */
public class GitBranchWidget extends EditorBasedWidget implements StatusBarWidget.TextPresentation, StatusBarWidget.Multiframe, GitBranchesListener {
  private volatile String myCurrentBranchName = "";
  private final GitBranches myBranches;

  public GitBranchWidget(Project project) {
    super(project);
    myBranches = GitBranches.getInstance(project);
    myBranches.addListener(this,this);
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

  @NotNull
  @Override
  public String getText() {
    final String text = myCurrentBranchName;
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

  @Override
  public void branchConfigurationChanged() {
    update();
  }

  private void update() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        final GitBranch currentBranch = myBranches.getCurrentBranch(getSelectedFile());
        String currentBranchName = currentBranch != null ? currentBranch.getName() : null;
        if (currentBranchName == null) {
          currentBranchName = "";
        }
        myCurrentBranchName = currentBranchName;
        myStatusBar.updateWidget(ID());
      }
    }, new Condition() {
      public boolean value(Object o) {
        Project project = getProject();
        return isDisposed() || project != null && (!project.isOpen() || project.isDisposed()) || myStatusBar == null;
      }
    });
  }
}
