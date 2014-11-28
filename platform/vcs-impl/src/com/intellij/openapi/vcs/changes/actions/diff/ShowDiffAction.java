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

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.DiffManager;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ShowDiffAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(ShowDiffAction.class);

  private static final String ourText = ActionsBundle.actionText("ChangesView.Diff");

  public ShowDiffAction() {
    super(ourText,
          ActionsBundle.actionDescription("ChangesView.Diff"),
          AllIcons.Actions.Diff);
  }

  public void update(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(CommonDataKeys.PROJECT);
    e.getPresentation().setEnabled(project != null && canShowDiff(changes));
  }

  protected static boolean canShowDiff(@Nullable Change[] changes) {
    return changes != null && changes.length != 0;
  }

  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) return;
    final Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes == null) return;

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

        // TODO: wtf ?
        if (convertedChanges.length == 1) {
          selectedChane = convertedChanges[0];
          ChangeList changeList = ((ChangeListManagerImpl)ChangeListManager.getInstance(project)).getIdentityChangeList(selectedChane);
          if (changeList != null) {
            result = changesInList != null ? changesInList : new ArrayList<Change>(changeList.getChanges());
          }
        }
        if (result == null) result = ContainerUtil.newArrayList(convertedChanges);

        ContainerUtil.sort(result, ChangesComparator.getInstance(false));
        int index = selectedChane == null ? 0 : Math.max(0, ContainerUtil.indexOfIdentity(result, selectedChane));

        showDiffForChange(project, result, index);
      }
    };

    if (needsConversion) {
      ChangeListManager.getInstance(project)
        .invokeAfterUpdate(performer, InvokeAfterUpdateMode.BACKGROUND_CANCELLABLE, ourText, ModalityState.current());
    }
    else {
      performer.run();
    }
  }

  private static boolean checkIfThereAreFakeRevisions(final Project project, final Change[] changes) {
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
  private static Change[] loadFakeRevisions(final Project project, final Change[] changes) {
    List<Change> matchingChanges = new ArrayList<Change>();
    for (Change change : changes) {
      matchingChanges.addAll(ChangeListManager.getInstance(project).getChangesIn(ChangesUtil.getFilePath(change)));
    }
    return matchingChanges.toArray(new Change[matchingChanges.size()]);
  }

  public static void showDiffForChange(@NotNull Project project, @NotNull List<Change> changes) {
    showDiffForChange(project, changes, 0);
  }

  public static void showDiffForChange(@NotNull Project project, @NotNull List<Change> changes, int index) {
    showDiffForChange(project, changes, index, new ShowDiffContext());
  }

  public static void showDiffForChange(@NotNull Project project,
                                       @NotNull List<Change> changes,
                                       int index,
                                       @NotNull ShowDiffContext context) {
    if (changes.isEmpty()) return;
    if (index < 0 || index >= changes.size()) index = 0;

    List<ChangeDiffRequestPresentable> presentables = new ArrayList<ChangeDiffRequestPresentable>(changes.size());
    for (Change change : changes) {
      presentables.add(new ChangeDiffRequestPresentable(project, change));
    }

    DiffRequestChain chain = new ChangeDiffRequestChain(presentables);
    chain.setIndex(index);

    // TODO: pass context data

    DiffManager.getInstance().showDiff(project, chain, context.getDialogHints());
  }
}
