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
package com.intellij.openapi.vcs.update;

import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.history.ByteContent;
import com.intellij.history.Label;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class ShowUpdatedDiffActionProvider implements AnActionExtensionProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isActive(@NotNull AnActionEvent e) {
    return isVisible(e.getDataContext());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DataContext dc = e.getDataContext();

    final Presentation presentation = e.getPresentation();
    presentation.setDescription(VcsBundle.messagePointer("action.presentation.ShowUpdatedDiffActionProvider.description"));

    //presentation.setVisible(isVisible(dc));
    presentation.setEnabled(isVisible(dc) && isEnabled(dc));
  }

  private static boolean isVisible(final DataContext dc) {
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    return (project != null) && (UpdateInfoTree.LABEL_BEFORE.getData(dc) != null) && (UpdateInfoTree.LABEL_AFTER.getData(dc) != null);
  }

  private static boolean isEnabled(final DataContext dc) {
    final Iterable<Pair<FilePath, FileStatus>> iterable = UpdateInfoTree.UPDATE_VIEW_FILES_ITERABLE.getData(dc);
    return iterable != null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dc = e.getDataContext();
    if ((!isVisible(dc)) || (!isEnabled(dc))) return;

    Project project = CommonDataKeys.PROJECT.getData(dc);
    Iterable<Pair<FilePath, FileStatus>> iterable = e.getData(UpdateInfoTree.UPDATE_VIEW_FILES_ITERABLE);
    if (iterable == null) return;
    Label before = e.getData(UpdateInfoTree.LABEL_BEFORE);
    Label after = e.getData(UpdateInfoTree.LABEL_AFTER);
    if (before == null || after == null) return;
    FilePath selectedUrl = UpdateInfoTree.UPDATE_VIEW_SELECTED_PATH.getData(dc);

    DiffRequestChain requestChain = createDiffRequestChain(project, before, after, iterable, selectedUrl);
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.FRAME);
  }

  public static ChangeDiffRequestChain createDiffRequestChain(@Nullable Project project,
                                                              @NotNull Label before,
                                                              @NotNull Label after,
                                                              @NotNull Iterable<? extends Pair<FilePath, FileStatus>> iterable,
                                                              @Nullable FilePath selectedPath) {
    List<MyDiffRequestProducer> requests = new ArrayList<>();
    int selected = -1;
    for (Pair<FilePath, FileStatus> pair : iterable) {
      if (selected == -1 && pair.first.equals(selectedPath)) selected = requests.size();
      requests.add(new MyDiffRequestProducer(project, before, after, pair.first, pair.second));
    }
    if (selected == -1) selected = 0;

    return new ChangeDiffRequestChain(requests, selected);
  }

  private static class MyDiffRequestProducer implements DiffRequestProducer, ChangeDiffRequestChain.Producer {
    @Nullable private final Project myProject;
    @NotNull private final Label myBefore;
    @NotNull private final Label myAfter;

    @NotNull private final FileStatus myFileStatus;
    @NotNull private final FilePath myFilePath;

    MyDiffRequestProducer(@Nullable Project project,
                          @NotNull Label before,
                          @NotNull Label after,
                          @NotNull FilePath filePath,
                          @NotNull FileStatus fileStatus) {
      myProject = project;
      myBefore = before;
      myAfter = after;
      myFileStatus = fileStatus;
      myFilePath = filePath;
    }

    @NotNull
    @Override
    public String getName() {
      return myFilePath.getPresentableUrl();
    }

    @NotNull
    @Override
    public FilePath getFilePath() {
      return myFilePath;
    }

    @NotNull
    @Override
    public FileStatus getFileStatus() {
      return myFileStatus;
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator)
      throws DiffRequestProducerException, ProcessCanceledException {
      try {
        DiffContent content1;
        DiffContent content2;

        DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();

        if (FileStatus.ADDED.equals(myFileStatus)) {
          content1 = contentFactory.createEmpty();
        }
        else {
          byte[] bytes1 = loadContent(myFilePath, myBefore);
          content1 = contentFactory.createFromBytes(myProject, bytes1, myFilePath);
        }

        if (FileStatus.DELETED.equals(myFileStatus)) {
          content2 = contentFactory.createEmpty();
        }
        else {
          byte[] bytes2 = loadContent(myFilePath, myAfter);
          content2 = contentFactory.createFromBytes(myProject, bytes2, myFilePath);
        }

        String title = DiffRequestFactory.getInstance().getTitle(myFilePath);
        return new SimpleDiffRequest(title,
                                     content1,
                                     content2,
                                     VcsBundle.message("update.label.before.update"),
                                     VcsBundle.message("update.label.after.update"));
      }
      catch (IOException e) {
        throw new DiffRequestProducerException(VcsBundle.message("update.can.t.load.content"), e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      MyDiffRequestProducer producer = (MyDiffRequestProducer)o;
      return myBefore.equals(producer.myBefore) &&
             myAfter.equals(producer.myAfter) &&
             myFileStatus.equals(producer.myFileStatus) &&
             myFilePath.equals(producer.myFilePath);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myBefore, myAfter, myFileStatus, myFilePath);
    }
  }

  private static byte @NotNull [] loadContent(@NotNull FilePath path, @NotNull Label label) throws DiffRequestProducerException {
    ByteContent byteContent = label.getByteContent(path.getPath());

    if (byteContent == null || byteContent.isDirectory() || byteContent.getBytes() == null) {
      throw new DiffRequestProducerException(VcsBundle.message("update.can.t.load.content"));
    }

    return byteContent.getBytes();
  }
}
