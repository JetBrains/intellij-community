// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListChange;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.changes.InvokeAfterUpdateMode;
import com.intellij.openapi.vcs.changes.LocalChangeList;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PartialChangesUtil {
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
    if (!ContainerUtil.exists(changes, it -> it instanceof ChangeListChange)) return new ArrayList<>(changes);

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
    computeUnderChangeList(project, targetChangeList, null, () -> {
      task.run();
      return null;
    }, false);
  }

  public static <T> T computeUnderChangeList(@NotNull Project project,
                                                        @Nullable LocalChangeList targetChangeList,
                                                        @Nullable String title,
                                                        @NotNull Computable<T> task,
                                                        boolean shouldAwaitCLMRefresh) {
    ChangeListManagerImpl clm = ChangeListManagerImpl.getInstanceImpl(project);
    LocalChangeList oldDefaultList = clm.getDefaultChangeList();

    if (targetChangeList == null || targetChangeList.equals(oldDefaultList)) {
      return task.compute();
    }

    switchChangeList(clm, targetChangeList, oldDefaultList);
    ChangelistConflictTracker clmConflictTracker = clm.getConflictTracker();
    try {
      clmConflictTracker.setIgnoreModifications(true);
      return task.compute();
    }
    finally {
      clmConflictTracker.setIgnoreModifications(false);
      if (shouldAwaitCLMRefresh) {
        InvokeAfterUpdateMode mode = title != null
                                     ? InvokeAfterUpdateMode.BACKGROUND_NOT_CANCELLABLE
                                     : InvokeAfterUpdateMode.SILENT_CALLBACK_POOLED;
        clm.invokeAfterUpdate(() -> restoreChangeList(clm, targetChangeList, oldDefaultList), mode, title, ModalityState.NON_MODAL);
      }
      else {
        restoreChangeList(clm, targetChangeList, oldDefaultList);
      }
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
