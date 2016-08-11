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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ShowDiffAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ShowDiffAction.class);

  public void update(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      e.getPresentation().setEnabled(project != null && changes != null && changes.length > 0);
    }
    else {
      e.getPresentation().setEnabled(project != null && canShowDiff(project, changes));
    }
  }

  public static boolean canShowDiff(@Nullable Project project, @Nullable Change[] changes) {
    return changes != null && canShowDiff(project, Arrays.asList(changes));
  }

  public static boolean canShowDiff(@Nullable Project project, @Nullable List<Change> changes) {
    if (changes == null || changes.size() == 0) return false;
    for (Change change : changes) {
      if (ChangeDiffRequestProducer.canCreate(project, change)) return true;
    }
    return false;
  }

  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (project == null || !canShowDiff(project, changes)) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;

    final boolean needsConversion = checkIfThereAreFakeRevisions(project, changes);
    final List<Change> changesInList = e.getData(VcsDataKeys.CHANGES_IN_LIST_KEY);

    // this trick is essential since we are under some conditions to refresh changes;
    // but we can only rely on callback after refresh
    final Runnable performer = new Runnable() {
      public void run() {
        Change[] convertedChanges;
        if (needsConversion) {
          convertedChanges = loadFakeRevisions(project, changes);
        }
        else {
          convertedChanges = changes;
        }

        if (convertedChanges == null || convertedChanges.length == 0) {
          return;
        }

        Change selectedChane = null;
        List<Change> result = null;

        if (convertedChanges.length == 1) {
          selectedChane = convertedChanges[0];
          ChangeList changeList = ((ChangeListManagerImpl)ChangeListManager.getInstance(project)).getIdentityChangeList(selectedChane);
          if (changeList != null) {
            result = changesInList != null ? changesInList : new ArrayList<>(changeList.getChanges());
          }
        }
        if (result == null) result = ContainerUtil.newArrayList(convertedChanges);

        //ContainerUtil.sort(result, ChangesComparator.getInstance(false));
        int index = selectedChane == null ? 0 : Math.max(0, ContainerUtil.indexOfIdentity(result, selectedChane));

        showDiffForChange(project, result, index);
      }
    };

    if (needsConversion) {
      ChangeListManager.getInstance(project).invokeAfterUpdate(performer, InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE,
                                                               ActionsBundle.actionText("ChangesView.Diff"), ModalityState.current());
    }
    else {
      performer.run();
    }
  }

  private static boolean checkIfThereAreFakeRevisions(@NotNull Project project, @NotNull Change[] changes) {
    boolean needsConversion = false;
    for (Change change : changes) {
      final ContentRevision beforeRevision = change.getBeforeRevision();
      final ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(beforeRevision.getFile());
        needsConversion = true;
      }
      if (afterRevision instanceof FakeRevision) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(afterRevision.getFile());
        needsConversion = true;
      }
    }
    return needsConversion;
  }

  @Nullable
  private static Change[] loadFakeRevisions(@NotNull Project project, @NotNull Change[] changes) {
    List<Change> matchingChanges = new ArrayList<>();
    for (Change change : changes) {
      matchingChanges.addAll(ChangeListManager.getInstance(project).getChangesIn(ChangesUtil.getFilePath(change)));
    }
    return matchingChanges.toArray(new Change[matchingChanges.size()]);
  }

  //
  // Impl
  //

  public static void showDiffForChange(@Nullable Project project, @NotNull Iterable<Change> changes) {
    showDiffForChange(project, changes, 0);
  }

  public static void showDiffForChange(@Nullable Project project, @NotNull Iterable<Change> changes, int index) {
    showDiffForChange(project, changes, index, new ShowDiffContext());
  }

  public static void showDiffForChange(@Nullable Project project,
                                       @NotNull Iterable<Change> changes,
                                       @NotNull Condition<Change> condition,
                                       @NotNull ShowDiffContext context) {
    int index = 0;
    List<ChangeDiffRequestProducer> presentables = new ArrayList<>();
    for (Change change : changes) {
      if (condition.value(change)) index = presentables.size();
      ChangeDiffRequestProducer presentable = ChangeDiffRequestProducer.create(project, change, context.getChangeContext(change));
      if (presentable != null) presentables.add(presentable);
    }

    showDiffForChange(project, presentables, index, context);
  }

  public static void showDiffForChange(@Nullable Project project,
                                       @NotNull Iterable<Change> changes,
                                       int index,
                                       @NotNull ShowDiffContext context) {
    int i = 0;
    int newIndex = 0;
    List<ChangeDiffRequestProducer> presentables = new ArrayList<>();
    for (Change change : changes) {
      if (i == index) newIndex = presentables.size();
      ChangeDiffRequestProducer presentable = ChangeDiffRequestProducer.create(project, change, context.getChangeContext(change));
      if (presentable != null) {
        presentables.add(presentable);
      }
      i++;
    }

    showDiffForChange(project, presentables, newIndex, context);
  }

  private static void showDiffForChange(@Nullable Project project,
                                        @NotNull List<ChangeDiffRequestProducer> presentables,
                                        int index,
                                        @NotNull ShowDiffContext context) {
    if (presentables.isEmpty()) return;
    if (index < 0 || index >= presentables.size()) index = 0;

    DiffRequestChain chain = new ChangeDiffRequestChain(presentables);
    chain.setIndex(index);

    for (Map.Entry<Key, Object> entry : context.getChainContext().entrySet()) {
      chain.putUserData(entry.getKey(), entry.getValue());
    }
    chain.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, context.getActions());

    DiffManager.getInstance().showDiff(project, chain, context.getDialogHints());
  }
}
