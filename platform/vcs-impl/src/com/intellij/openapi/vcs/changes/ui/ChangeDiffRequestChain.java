/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeGoToChangePopupAction;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ChangeDiffRequestChain extends UserDataHolderBase implements DiffRequestChain, GoToChangePopupBuilder.Chain {
  @NotNull private final List<? extends Producer> myProducers;
  private int myIndex;

  public ChangeDiffRequestChain(@NotNull List<? extends Producer> producers, int index) {
    myProducers = producers;
    myIndex = index;
  }

  @Override
  @NotNull
  public List<Producer> getRequests() {
    //noinspection unchecked
    return (List<Producer>)myProducers;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public void setIndex(int index) {
    assert index >= 0 && index < myProducers.size();
    myIndex = index;
  }

  @NotNull
  @Override
  public AnAction createGoToChangeAction(@NotNull Consumer<Integer> onSelected) {
    return new ChangeGoToChangePopupAction.Fake<ChangeDiffRequestChain>(this, myIndex, onSelected) {
      @NotNull
      @Override
      protected FilePath getFilePath(int index) {
        return myProducers.get(index).getFilePath();
      }

      @NotNull
      @Override
      protected FileStatus getFileStatus(int index) {
        return myProducers.get(index).getFileStatus();
      }
    };
  }

  public interface Producer extends DiffRequestProducer {
    @NotNull
    FilePath getFilePath();

    @NotNull
    FileStatus getFileStatus();
  }
}
