// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.diff.*;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.ItemLatestState;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.diff.DiffVcsDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

// TODO: remove duplication with ChangeDiffRequestProducer
public abstract class DiffActionExecutor {
  protected final DiffProvider myDiffProvider;
  protected final VirtualFile mySelectedFile;
  protected final Project myProject;
  private final Integer mySelectedLine;

  protected DiffActionExecutor(@NotNull DiffProvider diffProvider,
                               @NotNull VirtualFile selectedFile,
                               @NotNull Project project,
                               @Nullable Editor editor) {
    myDiffProvider = diffProvider;
    mySelectedFile = selectedFile;
    myProject = project;

    mySelectedLine = getSelectedLine(project, mySelectedFile, editor);
  }

  @Nullable
  private static Integer getSelectedLine(@NotNull Project project, @NotNull VirtualFile file, @Nullable Editor contextEditor) {
    Editor editor = null;
    if (contextEditor != null) {
      VirtualFile contextFile = FileDocumentManager.getInstance().getFile(contextEditor.getDocument());
      if (Comparing.equal(contextFile, file)) editor = contextEditor;
    }

    if (editor == null) {
      FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
      if (fileEditor instanceof TextEditor) editor = ((TextEditor)fileEditor).getEditor();
    }

    if (editor == null) return null;
    return editor.getCaretModel().getLogicalPosition().line;
  }

  @NotNull
  protected DiffContent createRemote(@NotNull ContentRevision fileRevision) throws IOException, VcsException {
    DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

    DiffContent diffContent;
    if (fileRevision instanceof ByteBackedContentRevision) {
      byte[] content = ((ByteBackedContentRevision)fileRevision).getContentAsBytes();
      if (content == null) throw new VcsException(VcsBundle.message("vcs.error.failed.to.load.file.content.from.vcs"));
      diffContent = contentFactory.createFromBytes(myProject, content, fileRevision.getFile());
    }
    else {
      String content = fileRevision.getContent();
      if (content == null) throw new VcsException(VcsBundle.message("vcs.error.failed.to.load.file.content.from.vcs"));
      diffContent = contentFactory.create(myProject, content, fileRevision.getFile());
    }

    diffContent.putUserData(DiffVcsDataKeys.REVISION_INFO, Pair.create(fileRevision.getFile(), fileRevision.getRevisionNumber()));
    return diffContent;
  }

  public void showDiff() {
    SimpleDiffRequestChain chain = SimpleDiffRequestChain.fromProducer(new MyDiffProducer());
    DiffManager.getInstance().showDiff(myProject, chain, DiffDialogHints.DEFAULT);
  }

  private class MyDiffProducer implements DiffRequestProducer {
    private final FilePath myFilePath = VcsUtil.getFilePath(mySelectedFile);

    @Override
    public @NotNull String getName() {
      return myFilePath.getPresentableUrl();
    }

    @Override
    public @NotNull DiffRequest process(@NotNull UserDataHolder context,
                                        @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      final ContentRevision contentRevision = getContentRevision();
      if (contentRevision == null) throw new DiffRequestProducerException(
        VcsBundle.message("diff.producer.error.cant.get.revision.content"));

      try {
        DiffContent content1 = createRemote(contentRevision);
        DiffContent content2 = DiffContentFactory.getInstance().create(myProject, mySelectedFile);

        String title = DiffRequestFactory.getInstance().getTitle(mySelectedFile);
        VcsRevisionNumber revisionNumber = contentRevision.getRevisionNumber();

        boolean inverted = false;
        String title1;
        String title2;
        final FileStatus status = ChangeListManager.getInstance(myProject).getStatus(mySelectedFile);
        boolean noLocalChanges = FileStatus.NOT_CHANGED.equals(status) ||
                                 FileStatus.UNKNOWN.equals(status) ||
                                 FileStatus.IGNORED.equals(status);
        final VcsRevisionNumber currentRevision = noLocalChanges ? myDiffProvider.getCurrentRevision(mySelectedFile) : null;
        if (currentRevision != null) {
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
          return request;
        }
        else {
          SimpleDiffRequest request = new SimpleDiffRequest(title, content1, content2, title1, title2);
          if (mySelectedLine != null) request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, mySelectedLine));
          request.putUserData(DiffUserDataKeys.MASTER_SIDE, Side.RIGHT);
          return request;
        }
      }
      catch (VcsException | IOException e) {
        throw new DiffRequestProducerException(e);
      }
    }
  }

  /**
   * @deprecated use {@link #showDiff(DiffProvider, VcsRevisionNumber, VirtualFile, Project)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
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
  protected abstract ContentRevision getContentRevision();

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

    @Override
    @Nullable
    protected ContentRevision getContentRevision() {
      return myDiffProvider.createFileContent(myNumber, mySelectedFile);
    }
  }

  public static class CompareToCurrentExecutor extends DiffActionExecutor {
    public CompareToCurrentExecutor(@NotNull DiffProvider diffProvider,
                                    @NotNull VirtualFile selectedFile,
                                    @NotNull Project project,
                                    @Nullable Editor editor) {
      super(diffProvider, selectedFile, project, editor);
    }

    @Override
    @Nullable
    protected ContentRevision getContentRevision() {
      return myDiffProvider.createCurrentFileContent(mySelectedFile);
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

    @Nullable
    @Override
    protected ContentRevision getContentRevision() {
      final ItemLatestState itemState = myDiffProvider.getLastRevision(mySelectedFile);
      if (itemState == null) return null;

      myFileStillExists = itemState.isItemExists();
      return myDiffProvider.createFileContent(itemState.getNumber(), mySelectedFile);
    }

    @NotNull
    @Override
    protected DiffContent createRemote(@NotNull ContentRevision fileRevision) throws IOException, VcsException {
      if (myFileStillExists) {
        return super.createRemote(fileRevision);
      }
      else {
        return DiffContentFactory.getInstance().createEmpty();
      }
    }
  }
}
