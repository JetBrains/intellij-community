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
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffTaskQueue;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.Collections;
import java.util.List;

public abstract class CacheChangeProcessor extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheChangeProcessor.class);

  @NotNull private final SoftHardCacheMap<Change, Pair<Change, DiffRequest>> myRequestCache =
    new SoftHardCacheMap<Change, Pair<Change, DiffRequest>>(5, 5);

  @Nullable private Change myCurrentChange;

  @NotNull private final DiffTaskQueue myQueue = new DiffTaskQueue();

  public CacheChangeProcessor(@NotNull Project project) {
    super(project);
  }

  public CacheChangeProcessor(@NotNull Project project, @NotNull String place) {
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

  @CalledInAwt
  public void updateRequest(final boolean force, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    final Change change = myCurrentChange;

    DiffRequest cachedRequest = loadRequestFast(change);
    if (cachedRequest != null) {
      applyRequest(cachedRequest, force, scrollToChangePolicy);
      return;
    }

    // TODO: check if current loading change is the same as we want to load now? (and not interrupt loading)
    myQueue.executeAndTryWait(
      new Function<ProgressIndicator, Runnable>() {
        @Override
        public Runnable fun(ProgressIndicator indicator) {
          final DiffRequest request = loadRequest(change, indicator);
          return new Runnable() {
            @Override
            @CalledInAwt
            public void run() {
              myRequestCache.put(change, Pair.create(change, request));
              applyRequest(request, force, scrollToChangePolicy);
            }
          };
        }
      },
      new Runnable() {
        @Override
        public void run() {
          applyRequest(new LoadingDiffRequest(ChangeDiffRequestProducer.getRequestTitle(change)), force, scrollToChangePolicy);
        }
      },
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    );
  }

  @Nullable
  @CalledInAwt
  @Contract("null -> !null")
  protected DiffRequest loadRequestFast(@Nullable Change change) {
    if (change == null) return NoDiffRequest.INSTANCE;

    Pair<Change, DiffRequest> pair = myRequestCache.get(change);
    if (pair != null) {
      Change oldChange = pair.first;
      if (ChangeDiffRequestProducer.isEquals(oldChange, change)) {
        return pair.second;
      }
    }

    if (change.getBeforeRevision() instanceof FakeRevision || change.getAfterRevision() instanceof FakeRevision) {
      return new LoadingDiffRequest(ChangeDiffRequestProducer.getRequestTitle(change));
    }

    return null;
  }

  @NotNull
  @CalledInBackground
  private DiffRequest loadRequest(@NotNull Change change, @NotNull ProgressIndicator indicator) {
    ChangeDiffRequestProducer presentable = ChangeDiffRequestProducer.create(getProject(), change);
    if (presentable == null) return new ErrorDiffRequest("Can't show diff");
    try {
      return presentable.process(getContext(), indicator);
    }
    catch (ProcessCanceledException e) {
      OperationCanceledDiffRequest request = new OperationCanceledDiffRequest(presentable.getName());
      request.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, Collections.<AnAction>singletonList(new ReloadRequestAction(change)));
      return request;
    }
    catch (DiffRequestProducerException e) {
      return new ErrorDiffRequest(presentable, e);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(presentable, e);
    }
  }

  //
  // Impl
  //

  @Override
  @CalledInAwt
  protected void onDispose() {
    super.onDispose();
    myQueue.abort();
    myRequestCache.clear();
  }

  @NotNull
  @Override
  public Project getProject() {
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

  public void clear() {
    myCurrentChange = null;
    updateRequest();
  }

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
    if (myCurrentChange == null) return false;

    List<Change> selectedChanges = getSelectedChanges();
    if (selectedChanges.isEmpty()) return false;

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      return index != -1 && index < selectedChanges.size() - 1;
    }
    else {
      List<Change> allChanges = getAllChanges();
      int index = allChanges.indexOf(myCurrentChange);
      return index != -1 && index < allChanges.size() - 1;
    }
  }

  @Override
  protected boolean hasPrevChange() {
    if (myCurrentChange == null) return false;

    List<Change> selectedChanges = getSelectedChanges();
    if (selectedChanges.isEmpty()) return false;

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      return index != -1 && index > 0;
    }
    else {
      List<Change> allChanges = getAllChanges();
      int index = allChanges.indexOf(myCurrentChange);
      return index != -1 && index > 0;
    }
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    List<Change> selectedChanges = getSelectedChanges();
    List<Change> allChanges = getAllChanges();

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      myCurrentChange = selectedChanges.get(index + 1);
    }
    else {
      int index = allChanges.indexOf(myCurrentChange);
      myCurrentChange = allChanges.get(index + 1);
      selectChange(myCurrentChange);
    }

    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    List<Change> selectedChanges = getSelectedChanges();
    List<Change> allChanges = getAllChanges();

    if (selectedChanges.size() > 1) {
      int index = selectedChanges.indexOf(myCurrentChange);
      myCurrentChange = selectedChanges.get(index - 1);
    }
    else {
      int index = allChanges.indexOf(myCurrentChange);
      myCurrentChange = allChanges.get(index - 1);
      selectChange(myCurrentChange);
    }

    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return getSelectedChanges().size() > 1 || getAllChanges().size() > 1;
  }

  //
  // Actions
  //

  protected class ReloadRequestAction extends DumbAwareAction {
    @NotNull private final Change myChange;

    public ReloadRequestAction(@NotNull Change change) {
      super("Reload", null, AllIcons.Actions.Refresh);
      myChange = change;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myRequestCache.remove(myChange);
      updateRequest(true);
    }
  }
}
