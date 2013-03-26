/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBus;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Kirill Likhodedov
 */
public class GitRootScanner implements BulkFileListener, ModuleRootListener, VcsListener {

  @NotNull private final GitRootProblemNotifier myRootProblemNotifier;

  private volatile boolean myProjectIsInitialized;
  private volatile boolean myMappingsAreReady;

  @NotNull private final Alarm myAlarm;
  private static final long WAIT_BEFORE_SCAN = TimeUnit.SECONDS.toMillis(1);

  public static void start(@NotNull Project project) {
    new GitRootScanner(project);
  }

  private GitRootScanner(@NotNull Project project) {
    myRootProblemNotifier = GitRootProblemNotifier.getInstance(project);

    StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        myProjectIsInitialized = true;
        scanIfReady();
      }
    });

    final MessageBus messageBus = project.getMessageBus();
    messageBus.connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, this);

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      String filePath = event.getPath();
      if (filePath != null && filePath.toLowerCase().endsWith(GitUtil.DOT_GIT)) {
        scanIfReady();
      }
    }
  }

  @Override
  public void beforeRootsChange(ModuleRootEvent event) {
  }

  @Override
  public void rootsChanged(ModuleRootEvent event) {
    scanIfReady();
  }

  @Override
  public void directoryMappingChanged() {
    myMappingsAreReady = true;
    scanIfReady();
  }

  private void scanIfReady() {
    if (readyToScan()) {
      scheduleScan();
    }
  }

  private boolean readyToScan() {
    return myMappingsAreReady && myProjectIsInitialized;
  }

  private void scheduleScan() {
    if (myAlarm.isDisposed()) {
      return;
    }

    myAlarm.cancelAllRequests(); // one scan is enough, no need to queue, they all do the same
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        myRootProblemNotifier.rescanAndNotifyIfNeeded();
      }
    }, WAIT_BEFORE_SCAN);
  }

}
