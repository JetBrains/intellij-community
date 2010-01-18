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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * need to update:
 * 1. if TS is null
 * 2. if checker returns TRUE -> those whose timestamp is older than required
 *
 */
@SomeQueue
public class LazyRefreshingSelfQueue<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.LazyRefreshingSelfQueue");

  private final Getter<Long> myUpdateInterval;
  // head is old. tail is new
  private final LinkedList<Pair<Long, T>> myQueue;
  private final Set<T> myInProgress;
  private final Computable<Boolean> myShouldUpdateOldChecker;
  private final Consumer<T> myUpdater;
  private final Object myLock;

  public LazyRefreshingSelfQueue(final Getter<Long> updateInterval, final Computable<Boolean> shouldUpdateOldChecker, final Consumer<T> updater) {
    myUpdateInterval = updateInterval;
    myShouldUpdateOldChecker = shouldUpdateOldChecker;
    myUpdater = updater;
    myQueue = new LinkedList<Pair<Long, T>>();
    myInProgress = new HashSet<T>();
    myLock = new Object();
  }

  public void addRequest(@NotNull final T t) {
    synchronized (myLock) {
      myQueue.addFirst(new Pair<Long,T>(null, t));
    }
  }

  public void addRequests(final Collection<T> values) {
    synchronized (myLock) {
      for (T value : values) {
        myQueue.addFirst(new Pair<Long,T>(null, value));
      }
    }
  }

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
  public void updateStep(@NotNull final ProgressIndicator pi) {
    final List<T> dirty = new LinkedList<T>();

    final long startTime = System.currentTimeMillis() - myUpdateInterval.get();
    boolean onlyAbsolute = true;
    // check if we have some old items at all - if not, we would not check if repository latest revision had changed and will save time
    synchronized (myLock) {
      for (Pair<Long, T> pair : myQueue) {
        if (pair.getFirst() != null) {
          onlyAbsolute = pair.getFirst() > startTime;
          break;
        }
      }
    }

    // do not ask under lock
    pi.checkCanceled();
    final Boolean shouldUpdateOld = onlyAbsolute ? false : myShouldUpdateOldChecker.compute();

    synchronized (myLock) {
      // get absolute
      while (! myQueue.isEmpty()) {
        pi.checkCanceled();
        final Pair<Long, T> pair = myQueue.get(0);
        if (pair.getFirst() == null) {
          dirty.add(myQueue.removeFirst().getSecond());
        } else {
          break;
        }
      }
      if (Boolean.TRUE.equals(shouldUpdateOld) && (! myQueue.isEmpty())) {
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
      pi.checkCanceled();
      myUpdater.consume(t);
      synchronized (myLock) {
        if (myInProgress.remove(t)) {
          myQueue.addLast(new Pair<Long,T>(System.currentTimeMillis(), t));
        }
      }
    }
  }
}
