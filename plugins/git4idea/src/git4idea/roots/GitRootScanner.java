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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Kirill Likhodedov
 */
public class GitRootScanner implements BulkFileListener, ModuleRootListener, Disposable, VcsListener {

  @NotNull private final Runnable myExecuteAfterScan;
  @NotNull private final GitRootProblemNotifier myRootProblemNotifier;

  private volatile boolean myProjectIsInitialized;
  private volatile boolean myMappingsAreReady;
  private volatile boolean myScanning;
  @NotNull private final Object SCAN_LOCK = new Object();

  public GitRootScanner(@NotNull Project project, @NotNull Runnable executeAfterScan) {
    myExecuteAfterScan = executeAfterScan;

    StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        myProjectIsInitialized = true;
      }
    });

    final MessageBus messageBus = project.getMessageBus();
    messageBus.connect().subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, this);
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this);
    messageBus.connect().subscribe(ProjectTopics.PROJECT_ROOTS, this);

    myRootProblemNotifier = GitRootProblemNotifier.getInstance(project);
  }

  @Override
  public void dispose() {
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file != null && file.getName().equalsIgnoreCase(".git") && file.isDirectory()) {
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
  }

  private void scanIfReady() {
    if (readyToScan()) {
      scan();
    }
  }

  private void scan() {
    if (myScanning) {
      return;
    }

    if (SwingUtilities.isEventDispatchThread()) {
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        public void run() {
          scanWithLock();
        }
      });
    }
    else {
      scanWithLock();
    }
  }

  private void scanWithLock() {
    synchronized (SCAN_LOCK) {
      if (myScanning) {
        return;
      }
      myScanning = true;
      myRootProblemNotifier.rescanAndNotifyIfNeeded();
      myExecuteAfterScan.run();
      myScanning = false;
    }
  }

  private boolean readyToScan() {
    return myMappingsAreReady && myProjectIsInitialized;
  }

}
