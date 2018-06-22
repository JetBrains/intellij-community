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
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class VcsRootScanner implements BulkFileListener, ModuleRootListener {

  @NotNull private final VcsRootProblemNotifier myRootProblemNotifier;
  @NotNull private final List<VcsRootChecker> myCheckers;

  @NotNull private final Alarm myAlarm;
  private static final long WAIT_BEFORE_SCAN = TimeUnit.SECONDS.toMillis(1);

  public static void start(@NotNull Project project, @NotNull List<VcsRootChecker> checkers) {
    new VcsRootScanner(project, checkers).scheduleScan();
  }

  private VcsRootScanner(@NotNull Project project, @NotNull List<VcsRootChecker> checkers) {
    myRootProblemNotifier = VcsRootProblemNotifier.getInstance(project);
    myCheckers = checkers;

    final MessageBus messageBus = project.getMessageBus();
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, this);

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      String filePath = event.getPath();
      if (myCheckers.stream().anyMatch(it -> it.isVcsDir(filePath))) {
        scheduleScan();
      }
    }
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
