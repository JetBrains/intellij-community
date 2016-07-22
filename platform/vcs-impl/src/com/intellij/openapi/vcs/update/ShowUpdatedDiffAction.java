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

import com.intellij.diff.*;
import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.Consumer;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ShowUpdatedDiffAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    final DataContext dc = e.getDataContext();

    final Presentation presentation = e.getPresentation();

    //presentation.setVisible(isVisible(dc));
    presentation.setEnabled(isVisible(dc) && isEnabled(dc));
  }

  private boolean isVisible(final DataContext dc) {
    final Project project = CommonDataKeys.PROJECT.getData(dc);
    return (project != null) && (VcsDataKeys.LABEL_BEFORE.getData(dc) != null) && (VcsDataKeys.LABEL_AFTER.getData(dc) != null);
  }

  private boolean isEnabled(final DataContext dc) {
    final Iterable<Pair<VirtualFilePointer, FileStatus>> iterable = VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE.getData(dc);
    return iterable != null;
  }

  public void actionPerformed(AnActionEvent e) {
    final DataContext dc = e.getDataContext();
    if ((!isVisible(dc)) || (!isEnabled(dc))) return;

    final Project project = CommonDataKeys.PROJECT.getData(dc);
    final Iterable<Pair<VirtualFilePointer, FileStatus>> iterable = e.getRequiredData(VcsDataKeys.UPDATE_VIEW_FILES_ITERABLE);
    final Label before = (Label)e.getRequiredData(VcsDataKeys.LABEL_BEFORE);
    final Label after = (Label)e.getRequiredData(VcsDataKeys.LABEL_AFTER);
    final String selectedUrl = VcsDataKeys.UPDATE_VIEW_SELECTED_PATH.getData(dc);

    MyDiffRequestChain requestChain = new MyDiffRequestChain(project, iterable, before, after, selectedUrl);
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.FRAME);
  }

  private static class MyDiffRequestChain extends UserDataHolderBase implements DiffRequestChain, GoToChangePopupBuilder.Chain {
    @Nullable private final Project myProject;
    @NotNull private final Label myBefore;
    @NotNull private final Label myAfter;
    @NotNull private final List<MyDiffRequestProducer> myRequests = new ArrayList<>();

    private int myIndex;

    public MyDiffRequestChain(@Nullable Project project,
                              @NotNull Iterable<Pair<VirtualFilePointer, FileStatus>> iterable,
                              @NotNull Label before,
                              @NotNull Label after,
                              @Nullable String selectedUrl) {
      myProject = project;
      myBefore = before;
      myAfter = after;

      int selected = -1;
      for (Pair<VirtualFilePointer, FileStatus> pair : iterable) {
        if (selected == -1 && pair.first.getUrl().equals(selectedUrl)) selected = myRequests.size();
        myRequests.add(new MyDiffRequestProducer(pair.first, pair.second));
      }
      if (selected != -1) myIndex = selected;
    }

    @NotNull
    @Override
    public List<MyDiffRequestProducer> getRequests() {
      return myRequests;
    }

    @Override
    public int getIndex() {
      return myIndex;
    }

    @Override
    public void setIndex(int index) {
      myIndex = index;
    }

    @NotNull
    @Override
    public AnAction createGoToChangeAction(@NotNull Consumer<Integer> onSelected) {
      return new ChangeGoToChangePopupAction.Fake<MyDiffRequestChain>(this, myIndex, onSelected) {
        @NotNull
        @Override
        protected FilePath getFilePath(int index) {
          return myRequests.get(index).getFilePath();
        }

        @NotNull
        @Override
        protected FileStatus getFileStatus(int index) {
          return myRequests.get(index).getFileStatus();
        }
      };
    }

    private class MyDiffRequestProducer implements DiffRequestProducer {
      @NotNull private final VirtualFilePointer myFilePointer;
      @NotNull private final FileStatus myFileStatus;
      @NotNull private final FilePath myFilePath;

      public MyDiffRequestProducer(@NotNull VirtualFilePointer filePointer, @NotNull FileStatus fileStatus) {
        myFilePointer = filePointer;
        myFileStatus = fileStatus;

        myFilePath = VcsUtil.getFilePath(myFilePointer.getPresentableUrl(), false);
      }

      @NotNull
      @Override
      public String getName() {
        return myFilePointer.getUrl();
      }

      @NotNull
      public FilePath getFilePath() {
        return myFilePath;
      }

      @NotNull
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

          if (FileStatus.ADDED.equals(myFileStatus)) {
            content1 = DiffContentFactory.getInstance().createEmpty();
          }
          else {
            byte[] bytes1 = loadContent(myFilePointer, myBefore);
            content1 = DiffContentFactoryImpl.getInstanceImpl().createFromBytes(myProject, myFilePath, bytes1);
          }

          if (FileStatus.DELETED.equals(myFileStatus)) {
            content2 = DiffContentFactory.getInstance().createEmpty();
          }
          else {
            byte[] bytes2 = loadContent(myFilePointer, myAfter);
            content2 = DiffContentFactoryImpl.getInstanceImpl().createFromBytes(myProject, myFilePath, bytes2);
          }

          String title = DiffRequestFactoryImpl.getContentTitle(myFilePath);
          return new SimpleDiffRequest(title, content1, content2, "Before update", "After update");
        }
        catch (IOException e) {
          throw new DiffRequestProducerException("Can't load content", e);
        }
      }
    }

    @NotNull
    private static byte[] loadContent(@NotNull VirtualFilePointer filePointer, @NotNull Label label) throws DiffRequestProducerException {
      String path = filePointer.getPresentableUrl();
      ByteContent byteContent = label.getByteContent(FileUtil.toSystemIndependentName(path));

      if (byteContent == null || byteContent.isDirectory() || byteContent.getBytes() == null) {
        throw new DiffRequestProducerException("Can't load content");
      }

      return byteContent.getBytes();
    }
  }
}
