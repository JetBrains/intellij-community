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
package com.intellij.openapi.vcs.roots;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.intellij.openapi.vfs.VirtualFileVisitor.*;

public class VcsRootScanner implements ModuleRootListener, AsyncVfsEventsListener {

  @NotNull private final VcsRootProblemNotifier myRootProblemNotifier;
  @NotNull private final Project myProject;
  @NotNull private final ProjectRootManager myProjectManager;
  @NotNull private final List<VcsRootChecker> myCheckers;

  @NotNull private final Alarm myAlarm;
  private static final long WAIT_BEFORE_SCAN = TimeUnit.SECONDS.toMillis(1);

  public static void start(@NotNull Project project, @NotNull List<VcsRootChecker> checkers) {
    new VcsRootScanner(project, checkers).scheduleScan();
  }

  private VcsRootScanner(@NotNull Project project, @NotNull List<VcsRootChecker> checkers) {
    myProject = project;
    myProjectManager = ProjectRootManager.getInstance(project);
    myRootProblemNotifier = VcsRootProblemNotifier.getInstance(project);
    myCheckers = checkers;

    AsyncVfsEventsPostProcessor.getInstance().addListener(this, project);
    project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, this);

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
  }

  @Override
  public void filesChanged(@NotNull List<VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file != null && file.isDirectory()) {
        visitDirsRecursivelyWithoutExcluded(myProject, myProjectManager, file, dir -> {
          if (isVcsDir(dir.getPath())) {
            scheduleScan();
            return skipTo(file);
          }
          return CONTINUE;
        });
      }
    }
  }

  static void visitDirsRecursivelyWithoutExcluded(@NotNull Project project,
                                                  @NotNull ProjectRootManager projectRootManager,
                                                  @NotNull VirtualFile root,
                                                  @NotNull Function<VirtualFile, Result> dirFound) {
    Option depthLimit = limit(Registry.intValue("vcs.root.detector.folder.depth"));
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor(NO_FOLLOW_SYMLINKS, depthLimit) {
      @NotNull
      @Override
      public VirtualFileVisitor.Result visitFileEx(@NotNull VirtualFile file) {
        ProgressManager.checkCanceled();
        if (!file.isDirectory()) {
          return CONTINUE;
        }

        VirtualFileVisitor.Result result = dirFound.apply(file);
        if (result != CONTINUE) {
          return result;
        }

        if (ReadAction.compute(() -> project.isDisposed() || projectRootManager.getFileIndex().isExcluded(file))) {
          return SKIP_CHILDREN;
        }

        return CONTINUE;
      }
    });
  }

  private boolean isVcsDir(@NotNull String filePath) {
    return myCheckers.stream().anyMatch(it -> it.isVcsDir(filePath));
  }

  @Override
  public void rootsChanged(ModuleRootEvent event) {
    scheduleScan();
  }

  private void scheduleScan() {
    if (myAlarm.isDisposed()) {
      return;
    }

    myAlarm.cancelAllRequests(); // one scan is enough, no need to queue, they all do the same
    myAlarm.addRequest(() ->
      BackgroundTaskUtil.runUnderDisposeAwareIndicator(myAlarm, () ->
        myRootProblemNotifier.rescanAndNotifyIfNeeded()), WAIT_BEFORE_SCAN);
  }
}
