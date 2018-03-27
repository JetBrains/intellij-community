/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ZipperUpdater;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 */
@SomeQueue
public class ZipAndQueue {
  private final ZipperUpdater myZipperUpdater;
  private final BackgroundTaskQueue myQueue;
  private Runnable myInZipper;
  private Task.Backgroundable myInvokedOnQueue;

  public ZipAndQueue(@NotNull Project project, final int interval, final String title, final Runnable runnable) {
    final int correctedInterval = interval <= 0 ? 300 : interval;
    myZipperUpdater = new ZipperUpdater(correctedInterval, project);
    myQueue = new BackgroundTaskQueue(project, title);
    myInZipper = () -> myQueue.run(myInvokedOnQueue);
    myInvokedOnQueue = new Task.Backgroundable(project, title, false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        runnable.run();
      }
    };
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        myZipperUpdater.stop();
      }
    });
  }

  public void request() {
    myZipperUpdater.queue(myInZipper);
  }

  public void stop() {
    myZipperUpdater.stop();
  }
}
