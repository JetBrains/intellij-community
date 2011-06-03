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
package com.intellij.openapi.project;

import com.intellij.ide.caches.CacheUpdater;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Future;

class CacheUpdateRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.project.CacheUpdateRunner");
  private static final int PROC_COUNT = Runtime.getRuntime().availableProcessors();
  private final Project myProject;
  private final Collection<CacheUpdater> myUpdaters;
  private CacheUpdateSession mySession;

  CacheUpdateRunner(Project project, Collection<CacheUpdater> updaters) {
    myProject = project;
    myUpdaters = updaters;
  }

  public int queryNeededFiles(ProgressIndicator indicator) {
    // can be queried twice in DumbService  
    if (mySession == null) {
      mySession = new CacheUpdateSession(myUpdaters, indicator);
    }
    return mySession.getFilesToUpdate().size();
  }

  public int getNumberOfPendingUpdateJobs(ProgressIndicator indicator) {
    if (mySession == null) {
      mySession = new CacheUpdateSession(myUpdaters, indicator);
    }
    return mySession.getNumberOfPendingUpdateJobs();
  }

  public void processFiles(final ProgressIndicator indicator, boolean processInReadAction) {
    try {
      indicator.checkCanceled();
      final FileContentQueue queue = new FileContentQueue();
      Collection<VirtualFile> files = mySession.getFilesToUpdate();
      final double total = files.size();
      queue.queue(files, indicator);

      Consumer<VirtualFile> progressUpdater = new Consumer<VirtualFile>() {
        // need set here to handle queue.pushbacks after checkCancelled() in order
        // not to count the same file several times
        final Set<VirtualFile> processed = new THashSet<VirtualFile>();

        public void consume(VirtualFile virtualFile) {
          indicator.checkCanceled();
          synchronized (processed) {
            processed.add(virtualFile);
            indicator.setFraction(processed.size() / total);
          }
          if (virtualFile.isValid()) {
            indicator.setText2(virtualFile.getPresentableUrl());
          }
          else {
            indicator.setText2("");
          }
        }
      };

      while (!myProject.isDisposed()) {
        indicator.checkCanceled();
        // todo wait for the user...
        if (processSomeFilesWhileUserIsInactive(queue, progressUpdater, processInReadAction)) {
          break;
        }
      }

      if (myProject.isDisposed()) {
        indicator.cancel();
        indicator.checkCanceled();
      }
    }
    catch (ProcessCanceledException e) {
      mySession.canceled();
      throw e;
    }
  }

  public void updatingDone() {
    try {
      mySession.updatingDone();
    }
    catch (ProcessCanceledException e) {
      mySession.canceled();
      throw e;
    }
  }

  private boolean processSomeFilesWhileUserIsInactive(final FileContentQueue queue,
                                                      final Consumer<VirtualFile> progressUpdater,
                                                      final boolean processInReadAction) {
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
    application.addApplicationListener(canceller);

    final Ref<Boolean> isFinished = new Ref<Boolean>(Boolean.FALSE);
    try {
      int threadsCount = Registry.intValue("caches.indexerThreadsCount");
      if (threadsCount <= 0) {
        threadsCount = Math.min(PROC_COUNT, 4);
      }
      if (threadsCount == 1) {
        Runnable process = new MyRunnable(innerIndicator, queue, isFinished, progressUpdater, processInReadAction, application);
        ProgressManager.getInstance().runProcess(process, innerIndicator);
      }
      else {
        final Ref[] finishedRefs = new Ref[threadsCount];
        Future<?>[] futures = new Future<?>[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
          final Ref<Boolean> ref = new Ref<Boolean>(Boolean.FALSE);
          finishedRefs[i] = ref;
          Runnable process = new MyRunnable(innerIndicator, queue, ref, progressUpdater, processInReadAction, application);
          futures[i] = ApplicationManager.getApplication().executeOnPooledThread(getProcessWrapper(process));
        }
        try {
          for (Future<?> future : futures) {
            future.get();
          }

          boolean allFinished = true;
          for (Ref ref : finishedRefs) {
            if (!(Boolean)ref.get()) {
              allFinished = false;
              break;
            }
          }
          isFinished.set(allFinished);
          
        }
        catch (Throwable throwable) {
          LOG.error(throwable);
        }
      }
    }
    finally {
      application.removeApplicationListener(canceller);
    }

    return isFinished.get();
  }

  private class MyRunnable implements Runnable {
    private final ProgressIndicatorBase myInnerIndicator;
    private final FileContentQueue myQueue;
    private final Ref<Boolean> myFinished;
    private final Consumer<VirtualFile> myProgressUpdater;
    private final boolean myProcessInReadAction;
    private final Application myApplication;

    public MyRunnable(ProgressIndicatorBase innerIndicator,
                      FileContentQueue queue,
                      Ref<Boolean> finished,
                      Consumer<VirtualFile> progressUpdater,
                      boolean processInReadAction, Application application) {
      myInnerIndicator = innerIndicator;
      myQueue = queue;
      myFinished = finished;
      myProgressUpdater = progressUpdater;
      myProcessInReadAction = processInReadAction;
      myApplication = application;
    }

    public void run() {
      while (true) {
        if (myProject.isDisposed()) return;
        if (myInnerIndicator.isCanceled()) return;

        final FileContent fileContent = myQueue.take();
        if (fileContent == null) {
          myFinished.set(Boolean.TRUE);
          return;
        }

        try {
          final Runnable action = new Runnable() {
            public void run() {
              myInnerIndicator.checkCanceled();

              if (myProject.isDisposed()) return;

              final VirtualFile file = fileContent.getVirtualFile();
              myProgressUpdater.consume(file);
              mySession.processFile(fileContent);
            }
          };
          if (myProcessInReadAction) {
            myApplication.runReadAction(action);
          }
          else {
            action.run();
          }
        }
        catch (ProcessCanceledException e) {
          myQueue.pushback(fileContent);
          return;
        }
        finally {
          if (fileContent != null) {
            myQueue.release(fileContent);
          }
        }
      }
    }
  }

  private static Runnable getProcessWrapper(final Runnable process) {
    // launching thread will hold read access for workers
    return ApplicationManager.getApplication().isReadAccessAllowed() ? new Runnable() {
      @Override
      public void run() {
        boolean old = ApplicationImpl.setExceptionalThreadWithReadAccessFlag(true);
        try {
          process.run();
        }
        finally {
          ApplicationImpl.setExceptionalThreadWithReadAccessFlag(old);
        }
      }
    } : process;
  }
}
