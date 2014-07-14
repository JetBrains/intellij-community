/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;

public class VcsInitialization {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsInitialization");

  private final List<Pair<VcsInitObject, Runnable>> myList = new ArrayList<Pair<VcsInitObject, Runnable>>();
  private final Object myLock;
  private boolean myInitStarted;
  private volatile Future<?> myFuture;

  public VcsInitialization(@NotNull final Project project) {
    myLock = new Object();

    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
      @Override
      public void run() {
        if (project.isDisposed()) return;
        myFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              execute();
            }
          }
        });
      }
    });
  }

  public void add(@NotNull final VcsInitObject vcsInitObject, @NotNull final Runnable runnable) {
    synchronized (myLock) {
      if (myInitStarted) {
        if (! vcsInitObject.isCanBeLast()) {
          LOG.info("Registering startup activity AFTER initialization ", new Throwable());
        }
        // post startup are normally called on awt thread
        ApplicationManager.getApplication().invokeLater(runnable);
        return;
      }
      myList.add(Pair.create(vcsInitObject, runnable));
    }
  }

  public void execute() {
    final List<Pair<VcsInitObject, Runnable>> list;
    synchronized (myLock) {
      list = myList;
      myInitStarted = true; // list would not be modified starting from this point
    }
    Collections.sort(list, new Comparator<Pair<VcsInitObject, Runnable>>() {
      @Override
      public int compare(Pair<VcsInitObject, Runnable> o1, Pair<VcsInitObject, Runnable> o2) {
        return o1.getFirst().getOrder() - o2.getFirst().getOrder();
      }
    });
    for (Pair<VcsInitObject, Runnable> pair : list) {
      pair.getSecond().run();
    }
  }

  @TestOnly
  public void waitForInitialized() {
    try {
      myFuture.get();
      myFuture = null;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
