/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.status.StatusBarUtil;
import com.intellij.util.ArrayUtil;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.validators.GitNewBranchNameValidator;
import org.intellij.images.editor.ImageFileEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * UI Utilities specific for Git branch features.
 *
 * @author Kirill Likhodedov
 */
public class GitBranchUiUtil {

  private GitBranchUiUtil() {
  }

  /**
   * Returns current branch name (if on branch) or current revision otherwise.
   * For fresh repository returns an empty string.
   */
  @NotNull
  public static String getBranchNameOrRev(@NotNull GitRepository repository) {
    if (repository.isOnBranch()) {
      GitBranch currentBranch = repository.getCurrentBranch();
      assert currentBranch != null;
      return currentBranch.getName();
    } else {
      String currentRevision = repository.getCurrentRevision();
      return currentRevision != null ? currentRevision.substring(0, 7) : "";
    }
  }

  /**
   * Shows a message dialog to enter the name of new branch.
   * @return name of new branch or {@code null} if user has cancelled the dialog.
   */
  @Nullable
  public static String getNewBranchNameFromUser(@NotNull Project project, @NotNull Collection<GitRepository> repositories, @NotNull String dialogTitle) {
    return Messages.showInputDialog(project, "Enter the name of new branch", dialogTitle, Messages.getQuestionIcon(), "",
                                    GitNewBranchNameValidator.newInstance(repositories));
  }

  /**
   * Returns the text that is displaying current branch.
   * In the simple case it is just the branch name, but in detached HEAD state it displays the hash or "rebasing master".
   */
  static String getDisplayableBranchText(@NotNull GitRepository repository) {
    GitRepository.State state = repository.getState();
    if (state == GitRepository.State.DETACHED) {
      String currentRevision = repository.getCurrentRevision();
      assert currentRevision != null : "Current revision can't be null in DETACHED state, only on the fresh repository.";
      return currentRevision.substring(0, 7);
    }

    String prefix = "";
    if (state == GitRepository.State.MERGING || state == GitRepository.State.REBASING) {
      prefix = state.toString() + " ";
    }

    GitBranch branch = repository.getCurrentBranch();
    String branchName = (branch == null ? "" : branch.getName());
    return prefix + branchName;
  }

  /**
   * Returns the currently selected file, based on which GitBranch components ({@link GitBranchPopup}, {@link GitBranchWidget})
   * will identify the current repository root.
   */
  @Nullable
  static VirtualFile getSelectedFile(@NotNull Project project) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    final FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(project, statusBar);
    VirtualFile result = null;
    if (fileEditor != null) {
      if (fileEditor instanceof TextEditor) {
        Document document = ((TextEditor)fileEditor).getEditor().getDocument();
        result = FileDocumentManager.getInstance().getFile(document);
      } else if (fileEditor instanceof ImageFileEditor) {
        result = ((ImageFileEditor)fileEditor).getImageEditor().getFile();
      }
    } 

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      if (manager != null) {
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          result = FileDocumentManager.getInstance().getFile(editor.getDocument()); 
        }
      }
    }
    return result;
  }

  /**
   * Guesses the Git root on which a Git action is to be invoked.
   * <ol>
   *   <li>
   *     Returns the root for the selected file. Selected file is determined by {@link #getSelectedFile(com.intellij.openapi.project.Project)}.
   *     If selected file is unknown (for example, no file is selected in the Project View or Changes View and no file is open in the editor),
   *     continues guessing. Otherwise returns the Git root for the selected file. If the file is not under a known Git root,  
   *     <code>null</code> will be returned - the file is definitely determined, but it is not under Git.
   *   </li>
   *   <li>
   *     Takes all Git roots registered in the Project. If there is only one, it is returned.
   *   </li>
   *   <li>
   *     If there are several Git roots, 
   *   </li>
   * </ol>
   * 
   * <p>
   *   NB: This method has to be accessed from the <b>read action</b>, because it may query
   *   {@link com.intellij.openapi.fileEditor.FileEditorManager#getSelectedTextEditor()}.
   * </p>
   * @param project current project
   * @return Git root that may be considered as "current".
   *         <code>null</code> is returned if a file not under Git was explicitly selected, if there are no Git roots in the project,
   *         or if the current Git root couldn't be determined.
   */
  @Nullable
  static VirtualFile guessGitRoot(@NotNull Project project) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs gitVcs = GitVcs.getInstance(project);
    if (gitVcs == null) {
      return null;
    }
    VirtualFile[] gitRoots = vcsManager.getRootsUnderVcs(gitVcs);
    if (gitRoots.length == 0) {
      return null;
    }

    VirtualFile selectedFile = getSelectedFile(project);
    if (selectedFile != null) {
      VirtualFile selectedFileRoot = vcsManager.getVcsRootFor(selectedFile);
      return ArrayUtil.contains(selectedFileRoot, gitRoots) ? selectedFileRoot : null;  // OK if this root is a Git root
    }

    // no selected files
    if (gitRoots.length == 1) {
      return gitRoots[0];
    }
    
    // TODO: remember last git root we have worked with
    return null;
  }
  
}
