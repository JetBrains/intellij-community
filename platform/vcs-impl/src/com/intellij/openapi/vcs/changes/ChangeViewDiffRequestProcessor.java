// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.FileContent;
import com.intellij.diff.impl.CacheDiffRequestProcessor;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.LoadingDiffRequest;
import com.intellij.diff.tools.util.PrevNextDifferenceIterable;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.actions.diff.PresentableGoToChangePopupAction;
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode;
import com.intellij.openapi.vcs.changes.ui.PresentableChange;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.util.containers.ContainerUtil.newArrayList;

public abstract class ChangeViewDiffRequestProcessor extends CacheDiffRequestProcessor.Simple
  implements DiffPreviewUpdateProcessor, DiffRequestProcessorWithProducers {

  private static final int MANY_CHANGES_THRESHOLD = 10000;

  @Nullable private Wrapper myCurrentChange;

  public ChangeViewDiffRequestProcessor(@NotNull Project project, @NotNull String place) {
    super(project, place);
  }

  //
  // Abstract
  //

  @Override
  public ListSelection<? extends DiffRequestProducer> collectDiffProducers(boolean selectedOnly) {
    Project project = getProject();
    Wrapper change = getCurrentChange();
    List<? extends Wrapper> changes = newArrayList(selectedOnly ? iterateSelectedChanges() : iterateAllChanges());
    return ListSelection.create(changes, change)
      .withExplicitSelection(selectedOnly)
      .map(wrapper -> wrapper.createProducer(project));
  }

  /**
   * @deprecated Use {@link #iterateSelectedChanges()}
   */
  @NotNull
  @Deprecated
  @ApiStatus.OverrideOnly
  public Stream<? extends Wrapper> getSelectedChanges() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated Use {@link #iterateAllChanges()}
   */
  @NotNull
  @Deprecated
  @ApiStatus.OverrideOnly
  public Stream<? extends Wrapper> getAllChanges() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public Iterable<? extends Wrapper> iterateSelectedChanges() {
    return JBIterable.from(getSelectedChanges().collect(Collectors.toList()));
  }

  @NotNull
  public Iterable<? extends Wrapper> iterateAllChanges() {
    return JBIterable.from(getAllChanges().collect(Collectors.toList()));
  }

  /**
   * Select change in view (ex: in corresponding JTree).
   * NB: might do nothing if existing multiple selection contains passed change.
   */
  protected abstract void selectChange(@NotNull Wrapper change);

  protected boolean showAllChangesForEmptySelection() {
    return true;
  }

  //
  // Update
  //

  @Override
  protected DiffRequestProducer getCurrentRequestProvider() {
    return myCurrentChange != null ? myCurrentChange.createProducer(getProject()) : null;
  }

  @Nullable
  @Override
  protected DiffRequest loadRequestFast(@NotNull DiffRequestProducer provider) {
    DiffRequest request = super.loadRequestFast(provider);
    return isRequestValid(request) ? request : null;
  }

  private static boolean isRequestValid(@Nullable DiffRequest request) {
    if (request instanceof ContentDiffRequest) {
      for (DiffContent content : ((ContentDiffRequest)request).getContents()) {
        // We compare CurrentContentRevision by their FilePath in cache map
        // If file was removed and then created again - we should not reuse request with old invalidated VirtualFile
        if (content instanceof FileContent && !((FileContent)content).getFile().isValid()) return false;
      }
    }
    return true;
  }

  public void updatePreview(boolean state, boolean fromModelRefresh) {
    if (state) {
      refresh(fromModelRefresh);
    }
    else {
      clear();
    }
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

  @Override
  public boolean isWindowFocused() {
    return DiffUtil.isFocusedComponent(getProject(), getComponent());
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

  @RequiresEdt
  @Override
  public void clear() {
    if (myCurrentChange != null) {
      myCurrentChange = null;
      updateRequest();
    }
    dropCaches();
  }

  @Override
  @RequiresEdt
  public void refresh(boolean fromModelRefresh) {
    if (isDisposed()) return;

    List<? extends Wrapper> selectedChanges = newArrayList(iterateSelectedChanges());
    if (selectedChanges.isEmpty() && showAllChangesForEmptySelection()) selectedChanges = newArrayList(iterateAllChanges());

    Wrapper selectedChange = myCurrentChange != null ? ContainerUtil.find(selectedChanges, myCurrentChange) : null;
    if (fromModelRefresh &&
        selectedChange == null &&
        myCurrentChange != null &&
        getContext().isWindowFocused() &&
        getContext().isFocusedInWindow()) {
      // Do not automatically switch focused viewer
      if (selectedChanges.size() == 1 && ContainerUtil.exists(iterateAllChanges(), it -> myCurrentChange.equals(it))) {
        selectChange(myCurrentChange); // Restore selection if necessary
      }
      return;
    }

    if (selectedChanges.isEmpty()) {
      setCurrentChange(null);
      return;
    }

    if (selectedChange == null) {
      setCurrentChange(selectedChanges.get(0));
      return;
    }

    setCurrentChange(selectedChange);
  }

  @Nullable
  @RequiresEdt
  public String getCurrentChangeName() {
    if (myCurrentChange == null) {
      return null;
    }
    return myCurrentChange.getPresentableName();
  }

  @RequiresEdt
  public void setCurrentChange(@Nullable Wrapper change) {
    myCurrentChange = change;
    updateRequest();
  }

  @Nullable
  public Wrapper getCurrentChange() {
    return myCurrentChange;
  }

  @Override
  protected @Nullable AnAction createGoToChangeAction() {
    return new MyGoToChangePopupAction();
  }

  private class MyGoToChangePopupAction extends PresentableGoToChangePopupAction.Default<Wrapper> {
    @Override
    protected @NotNull ListSelection<? extends Wrapper> getChanges() {
      List<? extends Wrapper> allChanges = newArrayList(iterateAllChanges());
      return ListSelection.create(allChanges, getCurrentChange());
    }

    @Override
    protected boolean canNavigate() {
      List<? extends Wrapper> allChanges = toListIfNotMany(iterateAllChanges(), true);
      return allChanges == null || allChanges.size() > 1;
    }

    @Override
    protected void onSelected(@NotNull Wrapper change) {
      setCurrentChange(change);
      selectChange(change);
    }
  }

  @Override
  protected boolean hasNextChange(boolean fromUpdate) {
    PrevNextDifferenceIterable strategy = getSelectionStrategy(fromUpdate);
    return strategy != null && strategy.canGoNext();
  }

  @Override
  protected boolean hasPrevChange(boolean fromUpdate) {
    PrevNextDifferenceIterable strategy = getSelectionStrategy(fromUpdate);
    return strategy != null && strategy.canGoPrev();
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    goToNextChangeImpl(fromDifferences, () -> {
      Objects.requireNonNull(getSelectionStrategy(false)).goNext();
    });
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    goToPrevChangeImpl(fromDifferences, () -> {
      Objects.requireNonNull(getSelectionStrategy(false)).goPrev();
    });
  }

  @Override
  protected boolean isNavigationEnabled() {
    return true;
  }

  @Nullable
  private PrevNextDifferenceIterable getSelectionStrategy(boolean fromUpdate) {
    if (myCurrentChange == null) return null;

    List<? extends Wrapper> selectedChanges = toListIfNotMany(iterateSelectedChanges(), fromUpdate);
    if (selectedChanges == null) return DumbPrevNextDifferenceIterable.INSTANCE;
    if (selectedChanges.size() > 1) {
      return new ChangesNavigatable(selectedChanges, selectedChanges.get(0), false);
    }
    if (selectedChanges.isEmpty() && !showAllChangesForEmptySelection()) {
      return null;
    }

    List<? extends Wrapper> allChanges = toListIfNotMany(iterateAllChanges(), fromUpdate);
    if (allChanges == null) return DumbPrevNextDifferenceIterable.INSTANCE;
    if (allChanges.isEmpty()) return null;

    Wrapper selection = selectedChanges.isEmpty() ? allChanges.get(0) : selectedChanges.get(0);
    return new ChangesNavigatable(allChanges, selection, true);
  }

  private class ChangesNavigatable implements PrevNextDifferenceIterable {
    @NotNull private final List<? extends Wrapper> myChanges;
    @NotNull private final Wrapper myFallback;
    private final boolean myUpdateSelection;

    ChangesNavigatable(@NotNull List<? extends Wrapper> allChanges, @NotNull Wrapper fallback, boolean updateSelection) {
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

  @Nullable
  public static <T> List<? extends T> toListIfNotMany(@NotNull Iterable<? extends T> iterable, boolean fromUpdate) {
    if (!fromUpdate) return newArrayList(iterable);

    List<? extends T> result = JBIterable.from(iterable).take(MANY_CHANGES_THRESHOLD + 1).toList();
    if (result.size() > MANY_CHANGES_THRESHOLD) return null;
    return result;
  }

  private static class DumbPrevNextDifferenceIterable implements PrevNextDifferenceIterable {
    public static final DumbPrevNextDifferenceIterable INSTANCE = new DumbPrevNextDifferenceIterable();

    @Override
    public boolean canGoPrev() {
      return true;
    }

    @Override
    public boolean canGoNext() {
      return true;
    }

    @Override
    public void goPrev() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void goNext() {
      throw new UnsupportedOperationException();
    }
  }

  public abstract static class Wrapper implements PresentableChange {

    @NotNull
    public abstract Object getUserObject();

    @Nullable
    public abstract String getPresentableName();

    @Nullable
    public abstract DiffRequestProducer createProducer(@Nullable Project project);

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (getClass() != o.getClass()) return false;

      Wrapper wrapper = (Wrapper)o;
      return Comparing.equal(getUserObject(), wrapper.getUserObject());
    }

    @Override
    public int hashCode() {
      return getUserObject().hashCode();
    }
  }

  public static class ChangeWrapper extends Wrapper {
    @NotNull protected final Change change;
    @Nullable protected final ChangesBrowserNode.Tag nodeTag;

    public ChangeWrapper(@NotNull Change change) {
      this(change, null);
    }

    public ChangeWrapper(@NotNull Change change, @Nullable ChangesBrowserNode.Tag nodeTag) {
      this.change = change;
      this.nodeTag = nodeTag;
    }

    @NotNull
    @Override
    public Object getUserObject() {
      return change;
    }

    @Override
    public @NotNull FilePath getFilePath() {
      return ChangesUtil.getFilePath(change);
    }

    @Override
    public @NotNull FileStatus getFileStatus() {
      return change.getFileStatus();
    }

    @Nullable
    @Override
    public String getPresentableName() {
      return getFilePath().getName();
    }

    @Override
    public @Nullable ChangesBrowserNode.Tag getTag() {
      return nodeTag;
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
      return ChangeListChange.HASHING_STRATEGY.equals(wrapper.change, change) && Objects.equals(wrapper.nodeTag, nodeTag);
    }

    @Override
    public int hashCode() {
      return Objects.hash(change, nodeTag);
    }
  }

  protected static class UnversionedFileWrapper extends Wrapper {
    @NotNull protected final FilePath path;

    public UnversionedFileWrapper(@NotNull FilePath path) {
      this.path = path;
    }

    @Override
    public @NotNull FilePath getFilePath() {
      return path;
    }

    @Override
    public @NotNull FileStatus getFileStatus() {
      return FileStatus.UNKNOWN;
    }

    @Override
    public @Nullable ChangesBrowserNode.Tag getTag() {
      return ChangesBrowserNode.UNVERSIONED_FILES_TAG;
    }

    @NotNull
    @Override
    public Object getUserObject() {
      return path;
    }

    @Nullable
    @Override
    public String getPresentableName() {
      return path.getName();
    }

    @Nullable
    @Override
    public DiffRequestProducer createProducer(@Nullable Project project) {
      return UnversionedDiffRequestProducer.create(project, path);
    }
  }

  private static class ErrorChangeRequestProducer implements DiffRequestProducer {
    @NotNull private final Change myChange;
    @NotNull private final DiffRequest myRequest;

    ErrorChangeRequestProducer(@NotNull Change change, @NotNull DiffRequest request) {
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
