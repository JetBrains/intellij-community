// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ex.LineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.ExclusionState;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashSet;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

public abstract class PartiallyExcludedFilesStateHolder<T> implements Disposable {
  @NotNull protected final MergingUpdateQueue myUpdateQueue =
    new MergingUpdateQueue(PartiallyExcludedFilesStateHolder.class.getName(), 300, true, MergingUpdateQueue.ANY_COMPONENT, this);

  private final Set<T> myIncludedElements = new THashSet<>();
  private final Map<T, ExclusionState> myTrackerExclusionStates = new HashMap<>();

  @NotNull private String myChangelistId;

  public PartiallyExcludedFilesStateHolder(@NotNull Project project, @NotNull String changelistId) {
    myChangelistId = changelistId;

    PartialLocalLineStatusTracker.Listener trackerListener = new MyTrackerListener();
    MyTrackerManagerListener trackerManagerListener = new MyTrackerManagerListener(trackerListener, this);
    trackerManagerListener.install(project);
  }

  @Override
  public void dispose() {
  }


  @NotNull
  protected abstract Stream<? extends T> getTrackableElementsStream();

  @Nullable
  protected abstract T findElementFor(@NotNull PartialLocalLineStatusTracker tracker);

  @Nullable
  protected abstract PartialLocalLineStatusTracker findTrackerFor(@NotNull T element);

  @NotNull
  private Stream<Pair<T, PartialLocalLineStatusTracker>> getTrackersStream() {
    return getTrackableElementsStream().<Pair<T, PartialLocalLineStatusTracker>>map(element -> {
      PartialLocalLineStatusTracker tracker = findTrackerFor(element);
      if (tracker != null) {
        return Pair.create(element, tracker);
      }
      else {
        return null;
      }
    }).filter(Objects::nonNull);
  }


  public void setChangelistId(@NotNull String changelistId) {
    myChangelistId = changelistId;
    updateExclusionStates();
  }

  @CalledInAwt
  public void updateExclusionStates() {
    myTrackerExclusionStates.clear();

    getTrackersStream().forEach(pair -> {
      T element = pair.first;
      PartialLocalLineStatusTracker tracker = pair.second;
      ExclusionState state = tracker.getExcludedFromCommitState(myChangelistId);
      if (state != ExclusionState.NO_CHANGES) myTrackerExclusionStates.put(element, state);
    });
  }

  @NotNull
  public ExclusionState getExclusionState(@NotNull T element) {
    ExclusionState exclusionState = myTrackerExclusionStates.get(element);
    if (exclusionState != null) return exclusionState;
    return myIncludedElements.contains(element) ? ExclusionState.ALL_INCLUDED
                                                : ExclusionState.ALL_EXCLUDED;
  }

  private void scheduleExclusionStatesUpdate() {
    myUpdateQueue.queue(new Update("updateExcludedFromCommit") {
      @Override
      public void run() {
        updateExclusionStates();
      }
    });
  }


  private class MyTrackerListener extends PartialLocalLineStatusTracker.ListenerAdapter {
    @Override
    public void onExcludedFromCommitChange(@NotNull PartialLocalLineStatusTracker tracker) {
      scheduleExclusionStatesUpdate();
    }

    @Override
    public void onChangeListMarkerChange(@NotNull PartialLocalLineStatusTracker tracker) {
      scheduleExclusionStatesUpdate();
    }
  }

  private class MyTrackerManagerListener extends LineStatusTrackerManager.ListenerAdapter {
    @NotNull private final PartialLocalLineStatusTracker.Listener myTrackerListener;
    @NotNull private final Disposable myDisposable;

    MyTrackerManagerListener(@NotNull PartialLocalLineStatusTracker.Listener listener, @NotNull Disposable disposable) {
      myTrackerListener = listener;
      myDisposable = disposable;
    }

    @CalledInAwt
    public void install(@NotNull Project project) {
      LineStatusTrackerManager.getInstanceImpl(project).addTrackerListener(this, myDisposable);
      for (LineStatusTracker<?> tracker : LineStatusTrackerManager.getInstanceImpl(project).getTrackers()) {
        if (tracker instanceof PartialLocalLineStatusTracker) {
          PartialLocalLineStatusTracker partialTracker = (PartialLocalLineStatusTracker)tracker;

          partialTracker.addListener(myTrackerListener, myDisposable);
        }
      }
    }

    @Override
    public void onTrackerAdded(@NotNull LineStatusTracker<?> tracker) {
      if (tracker instanceof PartialLocalLineStatusTracker) {
        PartialLocalLineStatusTracker partialTracker = (PartialLocalLineStatusTracker)tracker;

        T element = findElementFor(partialTracker);
        if (element != null) {
          partialTracker.setExcludedFromCommit(!myIncludedElements.contains(element));
        }

        partialTracker.addListener(myTrackerListener, myDisposable);
      }
    }

    @Override
    public void onTrackerRemoved(@NotNull LineStatusTracker<?> tracker) {
      if (tracker instanceof PartialLocalLineStatusTracker) {
        PartialLocalLineStatusTracker partialTracker = (PartialLocalLineStatusTracker)tracker;

        T element = findElementFor(partialTracker);
        if (element != null) {
          myTrackerExclusionStates.remove(element);

          ExclusionState exclusionState = partialTracker.getExcludedFromCommitState(myChangelistId);
          if (exclusionState != ExclusionState.NO_CHANGES) {
            if (exclusionState != ExclusionState.ALL_EXCLUDED) {
              myIncludedElements.add(element);
            }
            else {
              myIncludedElements.remove(element);
            }
          }

          scheduleExclusionStatesUpdate();
        }
      }
    }
  }


  public boolean isIncluded(@NotNull T element) {
    ExclusionState trackerState = getExclusionState(element);
    return trackerState != ExclusionState.ALL_EXCLUDED;
  }

  @NotNull
  public Set<T> getIncludedSet() {
    HashSet<T> set = new HashSet<>(myIncludedElements);

    for (Map.Entry<T, ExclusionState> entry : myTrackerExclusionStates.entrySet()) {
      T element = entry.getKey();
      ExclusionState trackerState = entry.getValue();

      if (trackerState == ExclusionState.ALL_EXCLUDED) {
        set.remove(element);
      }
      else {
        set.add(element);
      }
    }

    return set;
  }

  public void setIncludedElements(@NotNull Collection<? extends T> elements) {
    HashSet<T> set = new HashSet<>(elements);
    getTrackersStream().forEach(pair -> {
      T element = pair.first;
      PartialLocalLineStatusTracker tracker = pair.second;
      tracker.setExcludedFromCommit(!set.contains(element));
    });

    myIncludedElements.clear();
    myIncludedElements.addAll(elements);

    updateExclusionStates();
  }

  public void includeElements(@NotNull Collection<? extends T> elements) {
    for (T element : elements) {
      PartialLocalLineStatusTracker tracker = findTrackerFor(element);
      if (tracker != null) {
        tracker.setExcludedFromCommit(false);
      }
    }

    myIncludedElements.addAll(elements);

    updateExclusionStates();
  }

  public void excludeElements(@NotNull Collection<? extends T> elements) {
    for (T element : elements) {
      PartialLocalLineStatusTracker tracker = findTrackerFor(element);
      if (tracker != null) {
        tracker.setExcludedFromCommit(true);
      }
    }

    myIncludedElements.removeAll(elements);

    updateExclusionStates();
  }

  public void toggleElements(@NotNull Collection<? extends T> elements) {
    boolean hasExcluded = false;
    for (T element : elements) {
      ExclusionState exclusionState = getExclusionState(element);
      if (exclusionState != ExclusionState.ALL_INCLUDED) {
        hasExcluded = true;
        break;
      }
    }

    if (hasExcluded) {
      includeElements(elements);
    }
    else {
      excludeElements(elements);
    }
  }
}
