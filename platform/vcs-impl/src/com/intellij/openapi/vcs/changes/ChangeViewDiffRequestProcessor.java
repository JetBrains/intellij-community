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
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ChangeViewDiffRequestProcessor extends CacheDiffRequestProcessor<ChangeViewDiffRequestProcessor.ChangeWrapper>
  implements DiffPreviewUpdateProcessor {

  @Nullable private Change myCurrentChange;

  public ChangeViewDiffRequestProcessor(@NotNull Project project, @NotNull String place) {
    super(project, place);
  }

  //
  // Abstract
  //

  @NotNull
  protected abstract List<Change> getSelectedChanges();

  @NotNull
  protected abstract List<Change> getAllChanges();

  protected abstract void selectChange(@NotNull Change change);

  //
  // Update
  //


  @NotNull
  @Override
  protected String getRequestName(@NotNull ChangeWrapper wrapper) {
    return ChangeDiffRequestProducer.getRequestTitle(wrapper.change);
  }

  @Override
  protected ChangeWrapper getCurrentRequestProvider() {
    return myCurrentChange != null ? new ChangeWrapper(myCurrentChange) : null;
  }

  @Nullable
  @Override
  protected DiffRequest loadRequestFast(@NotNull ChangeWrapper wrapper) {
    DiffRequest request = super.loadRequestFast(wrapper);
    if (request != null) return request;

    if (wrapper.change.getBeforeRevision() instanceof FakeRevision || wrapper.change.getAfterRevision() instanceof FakeRevision) {
      return new LoadingDiffRequest(ChangeDiffRequestProducer.getRequestTitle(wrapper.change));
    }
    return null;
  }

  @NotNull
  @Override
  protected DiffRequest loadRequest(@NotNull ChangeWrapper provider, @NotNull ProgressIndicator indicator)
    throws ProcessCanceledException, DiffRequestProducerException {
    ChangeDiffRequestProducer presentable = ChangeDiffRequestProducer.create(getProject(), provider.change);
    if (presentable == null) return new ErrorDiffRequest(DiffBundle.message("error.cant.show.diff.message"));
    return presentable.process(getContext(), indicator);
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
    List<Change> selectedChanges = getSelectedChanges();

    if (selectedChanges.isEmpty()) {
      myCurrentChange = null;
      updateRequest();
      return;
    }

    Change selectedChange = myCurrentChange != null ? ContainerUtil.find(selectedChanges, myCurrentChange) : null;
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

    if (!ChangeDiffRequestProducer.isEquals(myCurrentChange, selectedChange)) {
      myCurrentChange = selectedChange;
      updateRequest();
    }
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
    List<Change> selectedChanges = getSelectedChanges();
    if (selectedChanges.isEmpty()) return null;
    if (selectedChanges.size() == 1) {
      return new ChangesNavigatable(getAllChanges(), selectedChanges.get(0), true);
    }
    return new ChangesNavigatable(selectedChanges, selectedChanges.get(0), false);
  }

  private class ChangesNavigatable implements PrevNextDifferenceIterable {
    @NotNull private final List<Change> myChanges;
    @NotNull private final Change myFallback;
    private final boolean myUpdateSelection;

    public ChangesNavigatable(@NotNull List<Change> allChanges, @NotNull Change fallback, boolean updateSelection) {
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

    private void select(@NotNull Change change) {
      myCurrentChange = change;
      if (myUpdateSelection) selectChange(change);
    }
  }


  protected static class ChangeWrapper {
    @NotNull private final Change change;

    private ChangeWrapper(@NotNull Change change) {
      this.change = change;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj.getClass() != getClass()) return false;
      return ChangeDiffRequestProducer.isEquals(((ChangeWrapper)obj).change, change);
    }

    @Override
    public int hashCode() {
      return ChangeDiffRequestProducer.hashCode(change);
    }

    @Override
    public String toString() {
      return String.format("ChangeViewDiffRequestProcessor.ChangeWrapper: %s (%s - %s)",
                           change.getClass(), toString(change.getBeforeRevision()), toString(change.getAfterRevision()));
    }

    @NotNull
    private static String toString(@Nullable ContentRevision revision) {
      if (revision == null) return "null";
      return revision.getClass() + ":" + revision.toString();
    }
  }
}
