// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker;
import com.intellij.openapi.vcs.ex.ExclusionState;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PairFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.ThreeStateCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

public final class PartialChangesUtil {
  private static final Logger LOG = getInstance(PartialChangesUtil.class);

  @Nullable
  public static PartialLocalLineStatusTracker getPartialTracker(@NotNull Project project, @NotNull Change change) {
    VirtualFile file = getVirtualFile(change);
    if (file == null) return null;

    return getPartialTracker(project, file);
  }

  @Nullable
  public static PartialLocalLineStatusTracker getPartialTracker(@NotNull Project project, @NotNull VirtualFile file) {
    LineStatusTracker<?> tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(file);
    return ObjectUtils.tryCast(tracker, PartialLocalLineStatusTracker.class);
  }

  @Nullable
  public static VirtualFile getVirtualFile(@NotNull Change change) {
    ContentRevision revision = change.getAfterRevision();
    if (!(revision instanceof CurrentContentRevision)) return null;

    return ((CurrentContentRevision)revision).getVirtualFile();
  }

  @NotNull
  public static List<Change> processPartialChanges(@NotNull Project project,
                                                   @NotNull Collection<? extends Change> changes,
                                                   boolean executeOnEDT,
                                                   @NotNull PairFunction<? super List<ChangeListChange>, ? super PartialLocalLineStatusTracker, Boolean> partialProcessor) {
    if (!LineStatusTrackerManager.getInstance(project).arePartialChangelistsEnabled() ||
        !ContainerUtil.exists(changes, it -> it instanceof ChangeListChange)) {
      return new ArrayList<>(changes);
    }

    List<Change> otherChanges = new ArrayList<>();

    Runnable task = () -> {
      MultiMap<VirtualFile, ChangeListChange> partialChangesMap = new MultiMap<>();
      for (Change change : changes) {
        if (change instanceof ChangeListChange) {
          ChangeListChange changelistChange = (ChangeListChange)change;

          VirtualFile virtualFile = getVirtualFile(change);
          if (virtualFile != null) {
            partialChangesMap.putValue(virtualFile, changelistChange);
          }
          else {
            otherChanges.add((changelistChange).getChange());
          }
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

        boolean success = partialProcessor.fun(partialChanges, tracker);
        if (!success) {
          otherChanges.add(actualChange);
        }
      }
    };

    if (executeOnEDT && !ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeAndWait(task);
    }
    else {
      task.run();
    }

    return otherChanges;
  }

  public static void runUnderChangeList(@NotNull Project project,
                                        @Nullable LocalChangeList targetChangeList,
                                        @NotNull Runnable task) {
    computeUnderChangeList(project, targetChangeList, () -> {
      task.run();
      return null;
    });
  }

  public static <T> T computeUnderChangeList(@NotNull Project project,
                                             @Nullable LocalChangeList targetChangeList,
                                             @NotNull Computable<T> task) {
    ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);
    LocalChangeList oldDefaultList = changeListManager.getDefaultChangeList();

    if (targetChangeList == null ||
        targetChangeList.equals(oldDefaultList) ||
        !changeListManager.areChangeListsEnabled()) {
      return task.compute();
    }

    switchChangeList(changeListManager, targetChangeList, oldDefaultList);
    ChangelistConflictTracker clmConflictTracker = ChangelistConflictTracker.getInstance(project);
    try {
      clmConflictTracker.setIgnoreModifications(true);
      return task.compute();
    }
    finally {
      clmConflictTracker.setIgnoreModifications(false);
      restoreChangeList(changeListManager, targetChangeList, oldDefaultList);
    }
  }

  public static <T> T computeUnderChangeListSync(@NotNull Project project,
                                                 @Nullable LocalChangeList targetChangeList,
                                                 @NotNull Computable<T> task) {
    ChangeListManagerEx changeListManager = ChangeListManagerEx.getInstanceEx(project);
    LocalChangeList oldDefaultList = changeListManager.getDefaultChangeList();

    if (targetChangeList == null ||
        !changeListManager.areChangeListsEnabled()) {
      return task.compute();
    }

    switchChangeList(changeListManager, targetChangeList, oldDefaultList);
    ChangelistConflictTracker clmConflictTracker = ChangelistConflictTracker.getInstance(project);
    try {
      clmConflictTracker.setIgnoreModifications(true);
      return task.compute();
    }
    finally {
      clmConflictTracker.setIgnoreModifications(false);

      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        LOG.warn("Can't wait till changes are applied while holding read lock", new Throwable());
      }
      else {
        ChangeListManagerEx.getInstanceEx(project).waitForUpdate();
      }
      restoreChangeList(changeListManager, targetChangeList, oldDefaultList);
    }
  }

  private static void switchChangeList(@NotNull ChangeListManagerEx clm,
                                       @NotNull LocalChangeList targetChangeList,
                                       @NotNull LocalChangeList oldDefaultList) {
    clm.setDefaultChangeList(targetChangeList, true);
    LOG.debug(String.format("Active changelist changed: %s -> %s", oldDefaultList.getName(), targetChangeList.getName()));
  }

  private static void restoreChangeList(@NotNull ChangeListManagerEx clm,
                                        @NotNull LocalChangeList targetChangeList,
                                        @NotNull LocalChangeList oldDefaultList) {
    LocalChangeList defaultChangeList = clm.getDefaultChangeList();
    if (Objects.equals(defaultChangeList.getId(), targetChangeList.getId())) {
      clm.setDefaultChangeList(oldDefaultList, true);
      LOG.debug(String.format("Active changelist restored: %s -> %s", targetChangeList.getName(), oldDefaultList.getName()));
    }
    else {
      LOG.warn(new Throwable(String.format("Active changelist was changed during the operation. Expected: %s -> %s, actual default: %s",
                                           targetChangeList.getName(), oldDefaultList.getName(), defaultChangeList.getName())));
    }
  }

  @NotNull
  public static ThreeStateCheckBox.State convertExclusionState(@NotNull ExclusionState exclusionState) {
    if (exclusionState == ExclusionState.ALL_INCLUDED) {
      return ThreeStateCheckBox.State.SELECTED;
    }
    else if (exclusionState == ExclusionState.ALL_EXCLUDED) {
      return ThreeStateCheckBox.State.NOT_SELECTED;
    }
    else {
      return ThreeStateCheckBox.State.DONT_CARE;
    }
  }
}
