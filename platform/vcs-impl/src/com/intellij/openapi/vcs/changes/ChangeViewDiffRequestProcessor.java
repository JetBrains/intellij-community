/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.impl.CacheDiffRequestProcessor;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.LoadingDiffRequest;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ChangeViewDiffRequestProcessor extends CacheDiffRequestProcessor<DiffRequestProducer>
  implements DiffPreviewUpdateProcessor {

  @Nullable private Wrapper myCurrentChange;

  public ChangeViewDiffRequestProcessor(@NotNull Project project, @NotNull String place) {
    super(project, place);
  }

  //
  // Abstract
  //

  @NotNull
  protected abstract List<Wrapper> getSelectedChanges();

  @NotNull
  protected abstract List<Wrapper> getAllChanges();

  protected abstract void selectChange(@NotNull Wrapper change);

  //
  // Update
  //


  @NotNull
  @Override
  protected String getRequestName(@NotNull DiffRequestProducer producer) {
    return producer.getName();
  }

  @Override
  protected DiffRequestProducer getCurrentRequestProvider() {
    return myCurrentChange != null ? myCurrentChange.createProducer(getProject()) : null;
  }

  @NotNull
  @Override
  protected DiffRequest loadRequest(@NotNull DiffRequestProducer producer, @NotNull ProgressIndicator indicator)
    throws ProcessCanceledException, DiffRequestProducerException {
    return producer.process(getContext(), indicator);
  }

  //
  // Impl
  //

  @NotNull
  @Override
  public Project getProject() {
    //noinspection ConstantConditions
    return super.getProject();
  }

  //
  // Navigation
  //

  /*
   * Multiple selection:
   * - iterate inside selection
   *
   * Single selection:
   * - iterate all changes
   * - update selection after movement
   *
   * current element should always be among allChanges and selection (if they are not empty)
   */

  @CalledInAwt
  @Override
  public void clear() {
    myCurrentChange = null;
    updateRequest();
  }

  @Override
  @CalledInAwt
  public void refresh() {
    List<Wrapper> selectedChanges = getSelectedChanges();

    if (selectedChanges.isEmpty()) {
      myCurrentChange = null;
      updateRequest();
      return;
    }

    Wrapper selectedChange = myCurrentChange != null ? ContainerUtil.find(selectedChanges, myCurrentChange) : null;
    if (selectedChange == null) {
      if (myCurrentChange != null && isFocused()) { // Do not automatically switch file if focused
        if (selectedChanges.size() == 1 && getAllChanges().contains(myCurrentChange)) {
          selectChange(myCurrentChange); // Restore selection if necessary
        }
        return;
      }

      myCurrentChange = selectedChanges.get(0);
      updateRequest();
      return;
    }

    myCurrentChange = selectedChange;
    updateRequest();
  }

  @Override
  protected boolean hasNextChange() {
    PrevNextDifferenceIterable strategy = getSelectionStrategy();
    return strategy != null && strategy.canGoNext();
  }

  @Override
  protected boolean hasPrevChange() {
    PrevNextDifferenceIterable strategy = getSelectionStrategy();
    return strategy != null && strategy.canGoPrev();
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    ObjectUtils.notNull(getSelectionStrategy()).goNext();
    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    ObjectUtils.notNull(getSelectionStrategy()).goPrev();
    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return true;
  }

  @Nullable
  private PrevNextDifferenceIterable getSelectionStrategy() {
    if (myCurrentChange == null) return null;
    List<Wrapper> selectedChanges = getSelectedChanges();
    if (selectedChanges.isEmpty()) return null;
    if (selectedChanges.size() == 1) {
      return new ChangesNavigatable(getAllChanges(), selectedChanges.get(0), true);
    }
    return new ChangesNavigatable(selectedChanges, selectedChanges.get(0), false);
  }

  private class ChangesNavigatable implements PrevNextDifferenceIterable {
    @NotNull private final List<Wrapper> myChanges;
    @NotNull private final Wrapper myFallback;
    private final boolean myUpdateSelection;

    public ChangesNavigatable(@NotNull List<Wrapper> allChanges, @NotNull Wrapper fallback, boolean updateSelection) {
      myChanges = allChanges;
      myFallback = fallback;
      myUpdateSelection = updateSelection;
    }

    @Override
    public boolean canGoNext() {
      if (myCurrentChange == null) return false;

      int index = myChanges.indexOf(myCurrentChange);
      return index == -1 || index < myChanges.size() - 1;
    }

    @Override
    public boolean canGoPrev() {
      if (myCurrentChange == null) return false;

      int index = myChanges.indexOf(myCurrentChange);
      return index == -1 || index > 0;
    }

    @Override
    public void goNext() {
      int index = myChanges.indexOf(myCurrentChange);
      if (index != -1) {
        select(myChanges.get(index + 1));
      }
      else {
        select(myFallback);
      }
    }

    @Override
    public void goPrev() {
      int index = myChanges.indexOf(myCurrentChange);
      if (index != -1) {
        select(myChanges.get(index - 1));
      }
      else {
        select(myFallback);
      }
    }

    private void select(@NotNull Wrapper change) {
      myCurrentChange = change;
      if (myUpdateSelection) selectChange(change);
    }
  }


  protected abstract static class Wrapper {
    @NotNull
    public abstract Object getUserObject();

    @Nullable
    public abstract DiffRequestProducer createProducer(@Nullable Project project);
  }

  protected static class ChangeWrapper extends Wrapper {
    @NotNull private final Change change;

    public ChangeWrapper(@NotNull Change change) {
      this.change = change;
    }

    @NotNull
    @Override
    public Object getUserObject() {
      return this.change;
    }

    @Nullable
    @Override
    public DiffRequestProducer createProducer(@Nullable Project project) {
      if (change.getBeforeRevision() instanceof FakeRevision || change.getAfterRevision() instanceof FakeRevision) {
        LoadingDiffRequest request = new LoadingDiffRequest(ChangeDiffRequestProducer.getRequestTitle(change));
        return new ErrorChangeRequestProducer(change, request);
      }

      ChangeDiffRequestProducer producer = ChangeDiffRequestProducer.create(project, change);

      if (producer == null) {
        ErrorDiffRequest request = new ErrorDiffRequest(DiffBundle.message("error.cant.show.diff.message"));
        return new ErrorChangeRequestProducer(change, request);
      }

      return producer;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (getClass() != o.getClass()) return false;

      ChangeWrapper wrapper = (ChangeWrapper)o;
      return ChangeListChange.HASHING_STRATEGY.equals(wrapper.change, change);
    }

    @Override
    public int hashCode() {
      return change.hashCode();
    }
  }

  protected static class UnversionedFileWrapper extends Wrapper {
    @NotNull private final VirtualFile file;

    public UnversionedFileWrapper(@NotNull VirtualFile file) {
      this.file = file;
    }

    @NotNull
    @Override
    public Object getUserObject() {
      return this.file;
    }

    @Nullable
    @Override
    public DiffRequestProducer createProducer(@Nullable Project project) {
      return UnversionedDiffRequestProducer.create(project, file);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (getClass() != o.getClass()) return false;

      UnversionedFileWrapper wrapper = (UnversionedFileWrapper)o;
      return wrapper.file.equals(file);
    }

    @Override
    public int hashCode() {
      return file.hashCode();
    }
  }

  private static class ErrorChangeRequestProducer implements DiffRequestProducer {
    @NotNull private final Change myChange;
    @NotNull private final DiffRequest myRequest;

    public ErrorChangeRequestProducer(@NotNull Change change, @NotNull DiffRequest request) {
      myChange = change;
      myRequest = request;
    }

    @NotNull
    @Override
    public String getName() {
      return ChangeDiffRequestProducer.getRequestTitle(myChange);
    }

    @NotNull
    @Override
    public DiffRequest process(@NotNull UserDataHolder context, @NotNull ProgressIndicator indicator) throws ProcessCanceledException {
      return myRequest;
    }
  }
}
