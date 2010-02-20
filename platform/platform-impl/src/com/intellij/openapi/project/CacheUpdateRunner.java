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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;

import java.util.Collection;
import java.util.Set;

class CacheUpdateRunner {
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
          processed.add(virtualFile);
          indicator.setFraction(processed.size() / total);
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
        if (processSomeFilesWhileUserIsInactive(queue, progressUpdater, mySession, processInReadAction)) {
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
                                                      final CacheUpdateSession session,
                                                      final boolean processInReadAction) {
    final ProgressIndicatorBase innerIndicator = new ProgressIndicatorBase();
    final ApplicationAdapter canceller = new ApplicationAdapter() {
      @Override
      public void beforeWriteActionStart(Object action) {
        innerIndicator.cancel();
      }
    };
    final Application application = ApplicationManager.getApplication();
    application.addApplicationListener(canceller);

    final boolean[] isFinished = new boolean[1];
    try {
      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          while (true) {
            if (myProject.isDisposed()) return;
            if (innerIndicator.isCanceled()) return;

            final FileContent fileContent = queue.take();
            if (fileContent == null) {
              isFinished[0] = true;
              return;
            }

            try {
              final Runnable action = new Runnable() {
                public void run() {
                  innerIndicator.checkCanceled();

                  if (myProject.isDisposed()) return;

                  final VirtualFile file = fileContent.getVirtualFile();
                  progressUpdater.consume(file);
                  session.processFile(fileContent);
                }
              };
              if (processInReadAction) {
                application.runReadAction(action);
              }
              else {
                action.run();
              }
            }
            catch (ProcessCanceledException e) {
              queue.pushback(fileContent);
              return;
            }
          }
        }
      }, innerIndicator);
    }
    finally {
      application.removeApplicationListener(canceller);
    }

    return isFinished[0];
  }
}
