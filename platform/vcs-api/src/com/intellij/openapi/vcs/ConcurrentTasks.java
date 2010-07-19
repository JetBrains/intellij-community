/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;

// competes to calculate The Result
public class ConcurrentTasks<T> {
  private volatile boolean myResultKnown;
  private final Semaphore mySemaphore;
  private volatile T myResult;
  private volatile int myCntAlive;
  private final ProgressIndicator myParentIndicator;
  private final Consumer<Consumer<T>>[] myTasks;

  public void compute() {
    final EmptyProgressIndicator pi = new EmptyProgressIndicator() {
      @Override
      public void checkCanceled() {
        if (myResultKnown || (myParentIndicator != null) && myParentIndicator.isCanceled()) {
          super.cancel();
        }
        super.checkCanceled();
      }
    };
    myCntAlive = myTasks.length;
    mySemaphore.down();

    final List<Future<?>> futures = new LinkedList<Future<?>>();
    for (final Consumer<Consumer<T>> task : myTasks) {
      if (myResultKnown) {
        -- myCntAlive;
        continue;
      }
      final Runnable computableProxy = new Runnable() {
        public void run() {
          try {
            task.consume(new Consumer<T>() {
              public void consume(T t) {
                if (myResultKnown) return;
                myResult = t;
                myResultKnown = true;
              }
            });
          }
          finally {
            -- myCntAlive;
            if (myCntAlive == 0 || myResultKnown) {
              mySemaphore.up();
            }
          }
        }
      };
      final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          ProgressManager.getInstance().runProcess(computableProxy, pi);
        }
      });
      futures.add(future);
    }

    while (true) {
      if (myResultKnown) break;
      if (myCntAlive <= 0) break;
      pi.checkCanceled();
      mySemaphore.waitFor(1000);
    }
    // in it possible to even interrupt() threads involved, but at the moment it's better for tasks themselves to check cancel status
    for (Future<?> future : futures) {
      if ((! future.isCancelled() && (! future.isDone()))) {
        future.cancel(true);
      }
    }
  }

  public boolean isResultKnown() {
    return myResultKnown;
  }

  public T getResult() {
    return myResult;
  }

  public ConcurrentTasks(final ProgressIndicator parentIndicator, final Consumer<Consumer<T>>... tasks) {
    myParentIndicator = parentIndicator;
    myTasks = tasks;
    mySemaphore = new Semaphore();
  }
}
