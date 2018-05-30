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

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ChangeListManager;
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

// TODO: remove duplication with ChangeDiffRequestProducer
public abstract class DiffActionExecutor {
  protected final DiffProvider myDiffProvider;
  protected final VirtualFile mySelectedFile;
  protected final Project myProject;
  private final Integer mySelectedLine;
  private final BackgroundableActionEnabledHandler myHandler;

  protected DiffActionExecutor(@NotNull DiffProvider diffProvider,
                               @NotNull VirtualFile selectedFile,
                               @NotNull Project project,
                               @Nullable Editor editor) {
    final ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManager.getInstance(project);
    myHandler = vcsManager.getBackgroundableActionHandler(VcsBackgroundableActions.COMPARE_WITH);
    myDiffProvider = diffProvider;
    mySelectedFile = selectedFile;
    myProject = project;

    mySelectedLine = getSelectedLine(project, mySelectedFile, editor);
  }

  @Nullable
  private Integer getSelectedLine(@NotNull Project project, @NotNull VirtualFile file, @Nullable Editor contextEditor) {
    Editor editor = null;
    if (contextEditor != null) {
      VirtualFile contextFile = FileDocumentManager.getInstance().getFile(contextEditor.getDocument());
      if (Comparing.equal(contextFile, mySelectedFile)) editor = contextEditor;
    }

    if (editor == null) {
      FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(mySelectedFile);
      if (fileEditor instanceof TextEditor) editor = ((TextEditor)fileEditor).getEditor();
    }

    if (editor == null) return null;
    return editor.getCaretModel().getLogicalPosition().line;
  }

  @Nullable
  protected DiffContent createRemote(final VcsRevisionNumber revisionNumber) throws IOException, VcsException {
    final ContentRevision fileRevision = myDiffProvider.createFileContent(revisionNumber, mySelectedFile);
    if (fileRevision == null) return null;
    DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

    DiffContent diffContent;
    if (fileRevision instanceof ByteBackedContentRevision) {
      byte[] content = ((ByteBackedContentRevision)fileRevision).getContentAsBytes();
      if (content == null) throw new VcsException("Failed to load content");
      diffContent = contentFactory.createFromBytes(myProject, content, fileRevision.getFile());
    }
    else {
      String content = fileRevision.getContent();
      if (content == null) throw new VcsException("Failed to load content");
      diffContent = contentFactory.create(myProject, content, fileRevision.getFile());
    }

    diffContent.putUserData(DiffUserDataKeysEx.REVISION_INFO, Pair.create(fileRevision.getFile(), fileRevision.getRevisionNumber()));
    return diffContent;
  }

  public void showDiff() {
    final Ref<VcsException> exceptionRef = new Ref<>();
    final Ref<DiffRequest> requestRef = new Ref<>();

    final Task.Backgroundable task = new Task.Backgroundable(myProject,
                                                             VcsBundle.message("show.diff.progress.title.detailed",
                                                                               mySelectedFile.getPresentableUrl()),
                                                             true) {

      public void run(@NotNull ProgressIndicator indicator) {
        final VcsRevisionNumber revisionNumber = getRevisionNumber();
        try {
          if (revisionNumber == null) {
            return;
          }
          DiffContent content1 = createRemote(revisionNumber);
          if (content1 == null) return;
          DiffContent content2 = DiffContentFactory.getInstance().create(myProject, mySelectedFile);

          String title = DiffRequestFactory.getInstance().getTitle(mySelectedFile);

          boolean inverted = false;
          String title1;
          String title2;
          final FileStatus status = ChangeListManager.getInstance(myProject).getStatus(mySelectedFile);
          if (FileStatus.NOT_CHANGED.equals(status) || FileStatus.UNKNOWN.equals(status) || FileStatus.IGNORED.equals(status)) {
            final VcsRevisionNumber currentRevision = myDiffProvider.getCurrentRevision(mySelectedFile);

            inverted = revisionNumber.compareTo(currentRevision) > 0;
            title1 = revisionNumber.asString();
            title2 = VcsBundle.message("diff.title.local.with.number", currentRevision.asString());
          }
          else {
            title1 = revisionNumber.asString();
            title2 = VcsBundle.message("diff.title.local");
          }

          if (inverted) {
            SimpleDiffRequest request = new SimpleDiffRequest(title, content2, content1, title2, title1);
            if (mySelectedLine != null) request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.LEFT, mySelectedLine));
            request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.LEFT);
            requestRef.set(request);
          }
          else {
            SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, title1, title2);
            if (mySelectedLine != null) request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, mySelectedLine));
            request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT);
            requestRef.set(request);
          }
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

        if (!exceptionRef.isNull()) {
          AbstractVcsHelper.getInstance(myProject).showError(exceptionRef.get(), VcsBundle.message("message.title.diff"));
          return;
        }
        if (!requestRef.isNull()) {
          DiffManager.getInstance().showDiff(myProject, requestRef.get());
        }
      }
    };

    myHandler.register(VcsBackgroundableActions.keyFrom(mySelectedFile));
    ProgressManager.getInstance().run(task);
  }

  @Deprecated
  public static void showDiff(final DiffProvider diffProvider, final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile,
                              final Project project, final VcsBackgroundableActions actionKey) {
    showDiff(diffProvider, revisionNumber, selectedFile, project);
  }

  public static void showDiff(final DiffProvider diffProvider, final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile,
                              final Project project) {
    final DiffActionExecutor executor = new CompareToFixedExecutor(diffProvider, selectedFile, project, null, revisionNumber);
    executor.showDiff();
  }

  @Nullable
  protected abstract VcsRevisionNumber getRevisionNumber();

  public static class CompareToFixedExecutor extends DiffActionExecutor {
    private final VcsRevisionNumber myNumber;

    public CompareToFixedExecutor(@NotNull DiffProvider diffProvider,
                                  @NotNull VirtualFile selectedFile,
                                  @NotNull Project project,
                                  @Nullable Editor editor,
                                  @NotNull VcsRevisionNumber number) {
      super(diffProvider, selectedFile, project, editor);
      myNumber = number;
    }

    protected VcsRevisionNumber getRevisionNumber() {
      return myNumber;
    }
  }

  public static class CompareToCurrentExecutor extends DiffActionExecutor {
    public CompareToCurrentExecutor(@NotNull DiffProvider diffProvider,
                                    @NotNull VirtualFile selectedFile,
                                    @NotNull Project project,
                                    @Nullable Editor editor) {
      super(diffProvider, selectedFile, project, editor);
    }

    @Nullable
    protected VcsRevisionNumber getRevisionNumber() {
      return myDiffProvider.getCurrentRevision(mySelectedFile);
    }
  }

  public static class DeletionAwareExecutor extends DiffActionExecutor {
    private boolean myFileStillExists;

    public DeletionAwareExecutor(@NotNull DiffProvider diffProvider,
                                 @NotNull VirtualFile selectedFile,
                                 @NotNull Project project,
                                 @Nullable Editor editor) {
      super(diffProvider, selectedFile, project, editor);
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
        return DiffContentFactory.getInstance().createEmpty();
      }
    }
  }
}
