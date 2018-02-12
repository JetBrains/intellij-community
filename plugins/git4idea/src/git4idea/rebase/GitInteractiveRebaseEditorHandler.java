/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.config.GitConfigUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.intellij.CommonBundle.getCancelButtonText;
import static com.intellij.CommonBundle.getOkButtonText;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.util.text.StringUtil.splitByLinesKeepSeparators;
import static git4idea.DialogManager.showOkCancelDialog;
import static git4idea.rebase.GitRebaseEditorMain.ERROR_EXIT_CODE;

/**
 * The handler for rebase editor request. The handler shows the {@link GitRebaseEditor}
 * dialog with the specified file. If user accepts the changes, it saves file and returns 0,
 * otherwise it just returns error code.
 */
public class GitInteractiveRebaseEditorHandler implements Closeable, GitRebaseEditorHandler {
  private final static Logger LOG = Logger.getInstance(GitInteractiveRebaseEditorHandler.class);
  private final GitRebaseEditorService myService;
  private final Project myProject;
  private final VirtualFile myRoot;
  @NotNull private final UUID myHandlerNo;
  private boolean myIsClosed;

  /**
   * If interactive rebase editor (with the list of commits) was shown, this is true.
   * In that case, the class expects only unstructured editor to edit the commit message.
   */
  protected boolean myRebaseEditorShown = false;

  private boolean myEditorCancelled;

  public GitInteractiveRebaseEditorHandler(@NotNull GitRebaseEditorService service, @NotNull Project project, @NotNull VirtualFile root) {
    myService = service;
    myProject = project;
    myRoot = root;
    myHandlerNo = service.registerHandler(this, project);
  }

  public int editCommits(@NotNull String path) {
    ensureOpen();
    try {
      if (myRebaseEditorShown) {
        myEditorCancelled = !handleUnstructuredEditor(path);
        return 0;
      }
      else {
        setRebaseEditorShown();
        boolean success = handleInteractiveEditor(path);
        if (success) {
          return 0;
        }
        else {
          myEditorCancelled = true;
          return ERROR_EXIT_CODE;
        }
      }
    }
    catch (Exception e) {
      LOG.error("Failed to edit git rebase file: " + path, e);
      return ERROR_EXIT_CODE;
    }
  }

  protected boolean handleUnstructuredEditor(@NotNull String path) throws IOException {
    String encoding = GitConfigUtil.getCommitEncoding(myProject, myRoot);
    File file = new File(path);
    String initialText = ignoreComments(FileUtil.loadFile(file, encoding));

    String newText = showUnstructuredEditor(initialText);
    if (newText == null) {
      return false;
    }
    else {
      FileUtil.writeToFile(file, newText.getBytes(encoding));
      return true;
    }
  }

  @NotNull
  private static String ignoreComments(@NotNull String text) {
    String[] lines = splitByLinesKeepSeparators(text);
    return StreamEx.of(lines)
      .filter(line -> !line.startsWith(GitUtil.COMMENT_CHAR))
      .joining();
  }

  @Nullable
  private String showUnstructuredEditor(@NotNull String initialText) {
    Ref<String> newText = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitRebaseUnstructuredEditor editor = new GitRebaseUnstructuredEditor(myProject, myRoot, initialText);
      DialogManager.show(editor);
      if (editor.isOK()) {
        newText.set(editor.getText());
      }
    });
    return newText.get();
  }

  protected boolean handleInteractiveEditor(@NotNull String path) throws IOException {
    GitInteractiveRebaseFile rebaseFile = new GitInteractiveRebaseFile(myProject, myRoot, path);
    try {
      List<GitRebaseEntry> entries = rebaseFile.load();
      List<GitRebaseEntry> newEntries = showInteractiveRebaseEditor(entries);
      if (newEntries != null) {
        rebaseFile.save(newEntries);
        return true;
      }
      else {
        rebaseFile.cancel();
        return false;
      }
    }
    catch (GitInteractiveRebaseFile.NoopException e) {
      return confirmNoopRebase();
    }
  }

  @Nullable
  private List<GitRebaseEntry> showInteractiveRebaseEditor(@NotNull List<GitRebaseEntry> entries) {
    Ref<List<GitRebaseEntry>> newText = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      GitRebaseEditor editor = new GitRebaseEditor(myProject, myRoot, entries);
      DialogManager.show(editor);
      if (editor.isOK()) {
        newText.set(editor.getEntries());
      }
    });
    return newText.get();
  }

  private boolean confirmNoopRebase() {
    LOG.info("Noop situation while rebasing " + myRoot);
    String message = "There are no commits to rebase because the current branch is directly below the base branch, " +
                     "or they point to the same commit (the 'noop' situation).\n" +
                     "Do you want to continue (this will reset the current branch to the base branch)?";
    Ref<Boolean> result = Ref.create(false);
    ApplicationManager.getApplication().invokeAndWait(() -> result.set(
      Messages.OK == showOkCancelDialog(myProject, message, "Git Rebase", getOkButtonText(), getCancelButtonText(), getQuestionIcon())));
    return result.get();
  }

  /**
   * This method is invoked to indicate that this editor will be invoked in the rebase continuation action.
   */
  public void setRebaseEditorShown() {
    myRebaseEditorShown = true;
  }

  /**
   * Check that handler has not yet been closed
   */
  private void ensureOpen() {
    if (myIsClosed) {
      throw new IllegalStateException("The handler was already closed");
    }
  }

  public void close() {
    ensureOpen();
    myIsClosed = true;
    myService.unregisterHandler(myHandlerNo);
  }

  @Override
  @NotNull
  public UUID getHandlerNo() {
    return myHandlerNo;
  }

  @Override
  public boolean wasEditorCancelled() {
    return myEditorCancelled;
  }
}
