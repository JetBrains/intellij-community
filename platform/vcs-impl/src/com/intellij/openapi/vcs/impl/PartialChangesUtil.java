// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PartialChangesUtil {
  @Nullable
  public static PartialLocalLineStatusTracker getPartialTracker(@NotNull Project project, @NotNull Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (!(revision instanceof CurrentContentRevision)) return null;

    VirtualFile file = ((CurrentContentRevision)revision).getVirtualFile();
    if (file == null) return null;

    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(file);
    return ObjectUtils.tryCast(tracker, PartialLocalLineStatusTracker.class);
  }

  @NotNull
  public static List<Change> processPartialChanges(@NotNull Project project,
                                                   @NotNull Collection<Change> changes,
                                                   boolean executeOnEDT,
                                                   @NotNull PairFunction<List<ChangeListChange>, PartialLocalLineStatusTracker, Boolean> partialProcessor) {
    if (!ContainerUtil.exists(changes, it -> it instanceof ChangeListChange)) return new ArrayList<>(changes);

    List<Change> otherChanges = new ArrayList<>();

    Runnable task = () -> {
      MultiMap<VirtualFile, ChangeListChange> partialChangesMap = new MultiMap<>();
      for (Change change : changes) {
        if (change instanceof ChangeListChange) {
          ChangeListChange changelistChange = (ChangeListChange)change;

          ContentRevision afterRevision = change.getAfterRevision();
          if (afterRevision instanceof CurrentContentRevision) {
            VirtualFile virtualFile = ((CurrentContentRevision)afterRevision).getVirtualFile();
            if (virtualFile != null) {
              partialChangesMap.putValue(virtualFile, changelistChange);
              continue;
            }
          }

          otherChanges.add((changelistChange).getChange());
        }
        else {
          otherChanges.add(change);
        }
      }

      LineStatusTrackerManagerI lstManager = LineStatusTrackerManager.getInstance(project);
      for (Map.Entry<VirtualFile, Collection<ChangeListChange>> entry : partialChangesMap.entrySet()) {
        VirtualFile virtualFile = entry.getKey();
        List<ChangeListChange> partialChanges = (List<ChangeListChange>)entry.getValue();
        Change actualChange = partialChanges.get(0).getChange();

        PartialLocalLineStatusTracker tracker = ObjectUtils.tryCast(lstManager.getLineStatusTracker(virtualFile),
                                                                    PartialLocalLineStatusTracker.class);
        if (tracker == null) {
          otherChanges.add(actualChange);
          continue;
        }

        Set<String> selectedIds = ContainerUtil.map2Set(partialChanges, change -> change.getChangeListId());
        if (selectedIds.containsAll(tracker.getAffectedChangeListsIds())) {
          otherChanges.add(actualChange);
          continue;
        }

        boolean success = partialProcessor.fun(partialChanges, tracker);
        if (!success) {
          otherChanges.add(actualChange);
        }
      }
    };

    if (executeOnEDT && !ApplicationManager.getApplication().isDispatchThread()) {
      TransactionGuard.getInstance().submitTransactionAndWait(task);
    }
    else {
      task.run();
    }

    return otherChanges;
  }
}
