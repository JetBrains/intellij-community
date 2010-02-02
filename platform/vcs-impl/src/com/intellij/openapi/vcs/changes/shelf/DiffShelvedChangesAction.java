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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diff.DiffRequestFactory;
import com.intellij.openapi.diff.MergeRequest;
import com.intellij.openapi.diff.impl.patch.ApplyPatchContext;
import com.intellij.openapi.diff.impl.patch.ApplyPatchException;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.ShowDiffAction;
import com.intellij.openapi.vcs.changes.patch.ApplyPatchAction;
import com.intellij.openapi.vcs.changes.patch.PatchMergeRequestFactory;
import com.intellij.openapi.vcs.changes.ui.ChangesComparator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class DiffShelvedChangesAction extends AnAction implements DumbAware {
  public void actionPerformed(final AnActionEvent e) {
    showShelvedChangesDiff(e.getDataContext());
    /*Project project = e.getData(PlatformDataKeys.PROJECT);
    final ShelvedChangeList[] changeLists = e.getData(ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY);
    List<ShelvedChange> shelvedChanges = e.getData(ShelvedChangesViewManager.SHELVED_CHANGE_KEY);
    if ((shelvedChanges == null || shelvedChanges.isEmpty()) && changeLists != null && changeLists.length > 0) {
      shelvedChanges = changeLists [0].getChanges();
    }
    if (shelvedChanges != null && shelvedChanges.size() > 0) {
      ShelvedChange c = shelvedChanges.get(0);
      Change change = c.getChange(project);
      if (isConflictingChange(change)) {
        try {
          if (showConflictingChangeDiff(project, c)) {
            return;
          }
        }
        catch (Exception ex) {
          // ignore and fallback to regular diff
        }
      }
    }
    ActionManager.getInstance().getAction("ChangesView.Diff").actionPerformed(e);*/
  }

  public static void showShelvedChangesDiff(final DataContext dc) {
    Project project = PlatformDataKeys.PROJECT.getData(dc);
    ShelvedChangeList[] changeLists = ShelvedChangesViewManager.SHELVED_CHANGELIST_KEY.getData(dc);
    if (changeLists == null) {
      changeLists = ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY.getData(dc);
    }

    // selected changes inside lists
    List<ShelvedChange> shelvedChanges = ShelvedChangesViewManager.SHELVED_CHANGE_KEY.getData(dc);

    if (changeLists == null) return;

    Change toSelect = null;
    final List<ShelvedChange> changesFromFirstList = changeLists[0].getChanges();
    if (shelvedChanges != null) {
      for (final ShelvedChange fromList : changesFromFirstList) {
        for (ShelvedChange shelvedChange : shelvedChanges) {
          if (fromList.equals(shelvedChange)) {
            toSelect = fromList.getChange(project);
            break;
          }
        }
        if (toSelect != null) break;
      }
    }

    final Change[] changes = new Change[changesFromFirstList.size()];
    for (int i = 0; i < changesFromFirstList.size(); i++) {
      final ShelvedChange shelvedChange = changesFromFirstList.get(i);
      changes[i] = shelvedChange.getChange(project);
    }
    Arrays.sort(changes, ChangesComparator.getInstance());

    int toSelectIdx = 0;
    for (int i = 0; i < changes.length; i++) {
      if (toSelect == changes[i]) {
        toSelectIdx = i;
      }
    }
    ShowDiffAction.showDiffForChange(changes, toSelectIdx, project);
  }

  private static boolean showConflictingChangeDiff(final Project project, final ShelvedChange c) throws PatchSyntaxException, IOException {
    TextFilePatch patch = c.loadFilePatch();
    if (patch == null) return false;

    ApplyPatchContext context = new ApplyPatchContext(project.getBaseDir(), 0, false, false);
    VirtualFile f = patch.findFileToPatch(context);
    if (f == null) return false;

    return ApplyPatchAction.mergeAgainstBaseVersion(project, f, context, patch, ShelvedChangeDiffRequestFactory.INSTANCE) != null;
  }

  private static boolean isConflictingChange(final Change change) {
    ContentRevision afterRevision = change.getAfterRevision();
    if (afterRevision == null) return false;
    try {
      afterRevision.getContent();
    }
    catch(VcsException e) {
      if (e.getCause() instanceof ApplyPatchException) {
        return true;
      }
    }
    return false;
  }

  public void update(final AnActionEvent e) {
    ActionManager.getInstance().getAction("ChangesView.Diff").update(e);
  }

  private static class ShelvedChangeDiffRequestFactory implements PatchMergeRequestFactory {
    public static final ShelvedChangeDiffRequestFactory INSTANCE = new ShelvedChangeDiffRequestFactory();

    public MergeRequest createMergeRequest(final String leftText, final String rightText, final String originalContent, @NotNull final VirtualFile file,
                                           final Project project) {
      MergeRequest request = DiffRequestFactory.getInstance().create3WayDiffRequest(leftText, rightText, originalContent,
                                                                                    project,
                                                                                    null);
      request.setVersionTitles(new String[] {
        "Current Version",
        "Base Version",
        "Shelved Version"
      });
      request.setWindowTitle("Shelved Change Conflict for" + file.getPresentableUrl());
      return request;
    }
  }
}
