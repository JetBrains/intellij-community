/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.BackgroundableActionEnabledHandler;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;

public abstract class DiffActionExecutor {
  protected final DiffProvider myDiffProvider;
  protected final VirtualFile mySelectedFile;
  protected final Project myProject;
  private final BackgroundableActionEnabledHandler myHandler;

  protected DiffActionExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project,
                               final VcsBackgroundableActions actionKey) {
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(project);
    myHandler = vcsManager.getBackgroundableActionHandler(actionKey);
    myDiffProvider = diffProvider;
    mySelectedFile = selectedFile;
    myProject = project;
  }

  @Nullable
  protected DiffContent createRemote(final VcsRevisionNumber revisionNumber) throws IOException, VcsException {
    final ContentRevision fileRevision = myDiffProvider.createFileContent(revisionNumber, mySelectedFile);
    if (fileRevision instanceof BinaryContentRevision) {
      final byte[] a = mySelectedFile.contentsToByteArray();
      final byte[] content = ((BinaryContentRevision)fileRevision).getBinaryContent();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (Arrays.equals(a, content)) {
            Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.identical"), VcsBundle.message("message.title.diff"));
          } else {
            Messages.showInfoMessage(VcsBundle.message("message.text.binary.versions.are.different"), VcsBundle.message("message.title.diff"));
          }
        }
      }, ModalityState.NON_MODAL);
      return null;
    }

    if (fileRevision != null) {
      final String content = fileRevision.getContent();
      if (content == null) {
        throw new VcsException("Failed to load content");
      }
      return new SimpleContent(content, mySelectedFile.getFileType());
    }
    return null;
  }

  public void showDiff() {
    final Ref<VcsException> exceptionRef = new Ref<VcsException>();
    final Ref<SimpleDiffRequest> requestRef = new Ref<SimpleDiffRequest>();

    final Task.Backgroundable task = new Task.Backgroundable(myProject,
        VcsBundle.message("show.diff.progress.title.detailed", mySelectedFile.getPath()), true, BackgroundFromStartOption.getInstance()) {

      public void run(@NotNull ProgressIndicator indicator) {
        final VcsRevisionNumber revisionNumber = getRevisionNumber();
        try {
          if (revisionNumber == null) {
            return;
          }
          final DiffContent remote = createRemote(revisionNumber);
          if (remote == null) {
            return;
          }

          final SimpleDiffRequest request = new SimpleDiffRequest(myProject, mySelectedFile.getPresentableUrl());
          final Document document = FileDocumentManager.getInstance().getDocument(mySelectedFile);
          if (document == null) return;
          final DocumentContent content2 = new DocumentContent(myProject, document);

          final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(mySelectedFile);

          if (revisionNumber.compareTo(currentRevision) > 0) {
            request.setContents(content2, remote);
            request.setContentTitles(VcsBundle.message("diff.title.local"), revisionNumber.asString());
          }
          else {
            request.setContents(remote, content2);
            request.setContentTitles(revisionNumber.asString(), VcsBundle.message("diff.title.local"));
          }

          request.addHint(DiffTool.HINT_SHOW_FRAME);
          requestRef.set(request);
        }
        catch (ProcessCanceledException e) {
          //ignore
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
        catch (IOException e) {
          exceptionRef.set(new VcsException(e));
        }
      }

      @Override
      public void onCancel() {
        onSuccess();
      }

      @Override
      public void onSuccess() {
        myHandler.completed(VcsBackgroundableActions.keyFrom(mySelectedFile));

        if (! exceptionRef.isNull()) {
          AbstractVcsHelper.getInstance(myProject).showError(exceptionRef.get(), VcsBundle.message("message.title.diff"));
          return;
        }
        if (! requestRef.isNull()) {
          DiffManager.getInstance().getDiffTool().show(requestRef.get());
        }
      }
    };

    myHandler.register(VcsBackgroundableActions.keyFrom(mySelectedFile));
    ProgressManager.getInstance().run(task);
  }

  public static void showDiff(final DiffProvider diffProvider, final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile,
                              final Project project, final VcsBackgroundableActions actionKey) {
    final DiffActionExecutor executor = new CompareToFixedExecutor(diffProvider, selectedFile, project, revisionNumber, actionKey);
    executor.showDiff();
  }

  @Nullable
  protected abstract VcsRevisionNumber getRevisionNumber();

  public static class CompareToFixedExecutor extends DiffActionExecutor {
    private final VcsRevisionNumber myNumber;

    public CompareToFixedExecutor(final DiffProvider diffProvider,
                                  final VirtualFile selectedFile, final Project project, final VcsRevisionNumber number,
                                  final VcsBackgroundableActions actionKey) {
      super(diffProvider, selectedFile, project, actionKey);
      myNumber = number;
    }

    protected VcsRevisionNumber getRevisionNumber() {
      return myNumber;
    }
  }

  public static class CompareToCurrentExecutor extends DiffActionExecutor {
    public CompareToCurrentExecutor(final DiffProvider diffProvider, final VirtualFile selectedFile, final Project project,
                                    final VcsBackgroundableActions actionKey) {
      super(diffProvider, selectedFile, project, actionKey);
    }

    @Nullable
    protected VcsRevisionNumber getRevisionNumber() {
      return myDiffProvider.getCurrentRevision(mySelectedFile);
    }
  }

  public static class DeletionAwareExecutor extends DiffActionExecutor {
    private boolean myFileStillExists;

    public DeletionAwareExecutor(final DiffProvider diffProvider,
                                 final VirtualFile selectedFile, final Project project, final VcsBackgroundableActions actionKey) {
      super(diffProvider, selectedFile, project, actionKey);
    }

    protected VcsRevisionNumber getRevisionNumber() {
      final ItemLatestState itemState = myDiffProvider.getLastRevision(mySelectedFile);
      if (itemState == null) {
        return null;
      }
      myFileStillExists = itemState.isItemExists();
      return itemState.getNumber();
    }

    @Override
    protected DiffContent createRemote(final VcsRevisionNumber revisionNumber) throws IOException, VcsException {
      if (myFileStillExists) {
        return super.createRemote(revisionNumber);
      } else {
        return new SimpleContent("", mySelectedFile.getFileType());
      }
    }
  }
}
