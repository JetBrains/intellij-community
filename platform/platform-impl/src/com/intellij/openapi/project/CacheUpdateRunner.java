/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.project;

import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class CacheUpdateRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.CacheUpdateRunner");
  private static final Key<Boolean> FAILED_TO_INDEX = Key.create("FAILED_TO_INDEX");
  private static final int PROC_COUNT = Runtime.getRuntime().availableProcessors();

  private static final int FILE_SIZE_TO_SHOW_THRESHOLD = 500 * 1024;

  public static void processFiles(final ProgressIndicator indicator,
                                  boolean processInReadAction,
                                  Collection<VirtualFile> files,
                                  Project project, Consumer<FileContent> processor) {
    indicator.checkCanceled();
    final FileContentQueue queue = new FileContentQueue(files, indicator);
    final double total = files.size();
    queue.startLoading();

    Consumer<VirtualFile> progressUpdater = new Consumer<VirtualFile>() {
      // need set here to handle queue push-backs after checkCancelled() in order
      // not to count the same file several times
      final Set<VirtualFile> processed = new THashSet<VirtualFile>();
      private boolean fileNameWasShown;

      @Override
      public void consume(VirtualFile virtualFile) {
        indicator.checkCanceled();
        synchronized (processed) {
          boolean added = processed.add(virtualFile);
          indicator.setFraction(processed.size() / total);
          if (!added || (virtualFile.isValid() && virtualFile.getLength() > FILE_SIZE_TO_SHOW_THRESHOLD)) {
            indicator.setText2(virtualFile.getPresentableUrl());
            fileNameWasShown = true;
          } else if (fileNameWasShown) {
            indicator.setText2("");
            fileNameWasShown = false;
          }
        }
      }
    };

    while (!project.isDisposed()) {
      indicator.checkCanceled();
      // todo wait for the user...
      if (processSomeFilesWhileUserIsInactive(queue, progressUpdater, processInReadAction, project, processor)) {
        break;
      }
    }

    if (project.isDisposed()) {
      indicator.cancel();
      indicator.checkCanceled();
    }
  }

  private static boolean processSomeFilesWhileUserIsInactive(@NotNull FileContentQueue queue,
                                                             @NotNull Consumer<VirtualFile> progressUpdater,
                                                             final boolean processInReadAction,
                                                             @NotNull Project project,
                                                             @NotNull Consumer<FileContent> fileProcessor) {
    final ProgressIndicatorBase innerIndicator = new ProgressIndicatorBase() {
      @Override
      protected boolean isCancelable() {
        return true; // the inner indicator must be always cancelable
      }
    };
    final ApplicationAdapter canceller = new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        innerIndicator.cancel();
      }
    };
    final Application application = ApplicationManager.getApplication();
    application.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        application.addApplicationListener(canceller);
      }
    }, ModalityState.any());

    final AtomicBoolean isFinished = new AtomicBoolean();
    try {
      int threadsCount = indexingThreadCount();
      if (threadsCount == 1 || application.isWriteAccessAllowed()) {
        Runnable process = new MyRunnable(innerIndicator, queue, isFinished, progressUpdater, processInReadAction, project, fileProcessor);
        ProgressManager.getInstance().runProcess(process, innerIndicator);
      }
      else {
        AtomicBoolean[] finishedRefs = new AtomicBoolean[threadsCount];
        Future<?>[] futures = new Future<?>[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
          AtomicBoolean ref = new AtomicBoolean();
          finishedRefs[i] = ref;
          Runnable process = new MyRunnable(innerIndicator, queue, ref, progressUpdater, processInReadAction, project, fileProcessor);
          futures[i] = application.executeOnPooledThread(process);
        }
        isFinished.set(waitForAll(finishedRefs, futures));
      }
    }
    finally {
      application.removeApplicationListener(canceller);
    }

    return isFinished.get();
  }

  public static int indexingThreadCount() {
    int threadsCount = Registry.intValue("caches.indexerThreadsCount");
    if (threadsCount <= 0) {
      int coresToLeaveForOtherActivity = ApplicationManager.getApplication().isUnitTestMode() ? 0 : 1;
      threadsCount = Math.max(1, Math.min(PROC_COUNT - coresToLeaveForOtherActivity, 4));
    }
    return threadsCount;
  }

  private static boolean waitForAll(@NotNull AtomicBoolean[] finishedRefs, @NotNull Future<?>[] futures) {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    try {
      for (Future<?> future : futures) {
        future.get();
      }

      boolean allFinished = true;
      for (AtomicBoolean ref : finishedRefs) {
        if (!ref.get()) {
          allFinished = false;
          break;
        }
      }
      return allFinished;
    }
    catch (InterruptedException ignored) {
    }
    catch (Throwable throwable) {
      LOG.error(throwable);
    }
    return false;
  }

  private static class MyRunnable implements Runnable {
    private final ProgressIndicatorBase myInnerIndicator;
    private final FileContentQueue myQueue;
    private final AtomicBoolean myFinished;
    private final Consumer<VirtualFile> myProgressUpdater;
    private final boolean myProcessInReadAction;
    @NotNull private final Project myProject;
    @NotNull private final Consumer<FileContent> myProcessor;

    public MyRunnable(@NotNull ProgressIndicatorBase innerIndicator,
                      @NotNull FileContentQueue queue,
                      @NotNull AtomicBoolean finished,
                      @NotNull Consumer<VirtualFile> progressUpdater,
                      boolean processInReadAction,
                      @NotNull Project project,
                      @NotNull Consumer<FileContent> fileProcessor) {
      myInnerIndicator = innerIndicator;
      myQueue = queue;
      myFinished = finished;
      myProgressUpdater = progressUpdater;
      myProcessInReadAction = processInReadAction;
      myProject = project;
      myProcessor = fileProcessor;
    }

    @Override
    public void run() {
      while (true) {
        if (myProject.isDisposed() || myInnerIndicator.isCanceled()) {
          return;
        }
        try {
          final FileContent fileContent = myQueue.take(myInnerIndicator);
          if (fileContent == null) {
            myFinished.set(true);
            return;
          }

          final Runnable action = new Runnable() {
            @Override
            public void run() {
              myInnerIndicator.checkCanceled();
              if (!myProject.isDisposed()) {
                final VirtualFile file = fileContent.getVirtualFile();
                try {
                  myProgressUpdater.consume(file);
                  if (!file.isDirectory() && !Boolean.TRUE.equals(file.getUserData(FAILED_TO_INDEX))) {
                    myProcessor.consume(fileContent);
                  }
                }
                catch (ProcessCanceledException e) {
                  throw e;
                }
                catch (Throwable e) {
                  LOG.error("Error while indexing " + file.getPresentableUrl() + "\n" + "To reindex this file IDEA has to be restarted", e);
                  file.putUserData(FAILED_TO_INDEX, Boolean.TRUE);
                }
              }
            }
          };
          try {
            ProgressManager.getInstance().runProcess(
              new Runnable() {
                @Override
                public void run() {
                  if (myProcessInReadAction) {
                    // in wait methods we don't want to deadlock by grabbing write lock (or having it in queue) and trying to run read action in separate thread
                    if (!ApplicationManagerEx.getApplicationEx().tryRunReadAction(action)) {
                      throw new ProcessCanceledException();
                    }
                  }
                  else {
                    action.run();
                  }
                }
              },
              ProgressWrapper.wrap(myInnerIndicator)
            );
          }
          catch (ProcessCanceledException e) {
            myQueue.pushBack(fileContent);
            return;
          }
          finally {
            myQueue.release(fileContent);
          }
        }
        catch (ProcessCanceledException e) {
          return;
        }
      }
    }
  }
}