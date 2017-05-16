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

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.DialogManager;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.List;
import java.util.UUID;

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
    Ref<Boolean> isSuccess = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        myEditorCancelled = false;
        if (myRebaseEditorShown) {
          GitRebaseUnstructuredEditor editor = new GitRebaseUnstructuredEditor(myProject, myRoot, path);
          DialogManager.show(editor);
          if (editor.isOK()) {
            editor.save();
          }
          else {
            myEditorCancelled = true;
          }
          isSuccess.set(true);
          return;
        }
        else {
          setRebaseEditorShown();
          GitInteractiveRebaseFile rebaseFile = new GitInteractiveRebaseFile(myProject, myRoot, path);
          try {
            List<GitRebaseEntry> entries = rebaseFile.load();
            GitRebaseEditor editor = new GitRebaseEditor(myProject, myRoot, entries);
            DialogManager.show(editor);
            if (editor.isOK()) {
              rebaseFile.save(editor.getEntries());
              isSuccess.set(true);
              return;
            }
            else {
              rebaseFile.cancel();
              myEditorCancelled = true;
            }
          }
          catch (GitInteractiveRebaseFile.NoopException e) {
            LOG.info("Noop situation while rebasing " + myRoot);
            String message = "There are no commits to rebase because the current branch is directly below the base branch, " +
                             "or they point to the same commit (the 'noop' situation).\n" +
                             "Do you want to continue (this will reset the current branch to the base branch)?";
            int rebase = DialogManager.showOkCancelDialog(myProject, message, "Git Rebase", CommonBundle.getOkButtonText(),
                                                          CommonBundle.getCancelButtonText(), Messages.getQuestionIcon());
            if (rebase == Messages.OK) {
              isSuccess.set(true);
              return;
            }
            else {
              myEditorCancelled = true;
            }
          }
        }
      }
      catch (Exception e) {
        LOG.error("Failed to edit the git rebase file: " + path, e);
      }
      isSuccess.set(false);
    });
    return (isSuccess.isNull() || !isSuccess.get().booleanValue()) ? GitRebaseEditorMain.ERROR_EXIT_CODE : 0;
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

  /**
   * @return the handler number
   */
  @NotNull
  public UUID getHandlerNo() {
    return myHandlerNo;
  }

  public boolean wasEditorCancelled() {
    return myEditorCancelled;
  }
}
