// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * need to update:
 * 1. if TS is null
 * 2. if checker returns TRUE -> those whose timestamp is older than required
 *
 */
// TODO: Used only in RemoteRevisionsNumberCache
@ApiStatus.Internal
public final class LazyRefreshingSelfQueue<T> {
  private static final Logger LOG = Logger.getInstance(LazyRefreshingSelfQueue.class);

  // provides update interval in milliseconds.
  private final LongSupplier myUpdateInterval;
  // structure:
  // 1) pairs with First == null
  // 2) pairs with First != null sorted by First ascending
  // pair.First - time when T was last processed
  // pair.Second - some item T
  private final LinkedList<Pair<Long, T>> myQueue;
  // Set of items that should be processed by myUpdater
  private final Set<T> myInProgress;
  // checks if updateStep should be really performed
  private final Computable<Boolean> myShouldUpdateOldChecker;
  // performs some actions on item T, for instance - updates some data for T in cache
  private final Consumer<? super T> myUpdater;
  private final Object myLock;

  public LazyRefreshingSelfQueue(LongSupplier updateInterval, final Computable<Boolean> shouldUpdateOldChecker, final Consumer<? super T> updater) {
    myUpdateInterval = updateInterval;
    myShouldUpdateOldChecker = shouldUpdateOldChecker;
    myUpdater = updater;
    myQueue = new LinkedList<>();
    myInProgress = new HashSet<>();
    myLock = new Object();
  }

  // adds item that should be updated at next updateStep() call
  public void addRequest(@NotNull final T t) {
    synchronized (myLock) {
      myQueue.addFirst(new Pair<>(null, t));
    }
  }

  // unschedules item from update at next updateStep() call
  public void forceRemove(@NotNull final T t) {
    synchronized (myLock) {
      for (Iterator<Pair<Long, T>> iterator = myQueue.iterator(); iterator.hasNext();) {
        final Pair<Long, T> pair = iterator.next();
        if (t.equals(pair.getSecond())) {
          iterator.remove();
        }
      }
      myInProgress.remove(t);
    }
  }

  // called by outside timer or something
  public void updateStep() {
    final long startTime = System.currentTimeMillis() - myUpdateInterval.getAsLong();
    boolean onlyAbsolute = true;
    // TODO: Actually we could store items with pair.First == null in separate list.
    // checks item that has smallest update time - i.e. was not updated by the most time
    // if its update time greater than current - interval => we should not update any item with pair.First != null this time (as they are ordered)
    synchronized (myLock) {
      for (Pair<Long, T> pair : myQueue) {
        if (pair.getFirst() != null) {
          onlyAbsolute = pair.getFirst() > startTime;
          break;
        }
      }
    }

    // do not ask under lock
    final Boolean shouldUpdateOld = onlyAbsolute ? false : myShouldUpdateOldChecker.compute();
    final List<T> dirty = new ArrayList<>();

    synchronized (myLock) {
      // adds all pairs with pair.First == null to dirty
      while (! myQueue.isEmpty()) {
        final Pair<Long, T> pair = myQueue.get(0);
        if (pair.getFirst() == null) {
          dirty.add(myQueue.removeFirst().getSecond());
        } else {
          break;
        }
      }
      if (Boolean.TRUE.equals(shouldUpdateOld) && (! myQueue.isEmpty())) {
        // adds all pairs with update time (pair.First) < current - interval to dirty
        while (! myQueue.isEmpty()) {
          final Pair<Long, T> pair = myQueue.get(0);
          if (pair.getFirst() < startTime) {
            myQueue.removeFirst();
            dirty.add(pair.getSecond());
          } else {
            break;
          }
        }
      }

      myInProgress.addAll(dirty);
    }

    LOG.debug("found something to update: " + (! dirty.isEmpty()));
    for (T t : dirty) {
      myUpdater.consume(t);
      synchronized (myLock) {
        // output value of remove() is tracked not to process items that were removed from myInProgress in forceRemove()
        // TODO: Probably more clear logic should be implemented
        if (myInProgress.remove(t)) {
          myQueue.addLast(new Pair<>(System.currentTimeMillis(), t));
        }
      }
    }
  }
}
