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

/*
 * @author max
 */
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PushedFilePropertiesUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater");

  private final Project myProject;
  private final FilePropertyPusher[] myPushers;
  private final FilePropertyPusher[] myFilePushers;
  private final Queue<DumbModeTask> myTasks = new ConcurrentLinkedQueue<DumbModeTask>();

  public static PushedFilePropertiesUpdater getInstance(Project project) {
    return project.getComponent(PushedFilePropertiesUpdater.class);
  }

  public PushedFilePropertiesUpdater(final Project project, final MessageBus bus) {
    myProject = project;
    myPushers = Extensions.getExtensions(FilePropertyPusher.EP_NAME);
    myFilePushers = ContainerUtil.findAllAsArray(myPushers, new Condition<FilePropertyPusher>() {
      @Override
      public boolean value(FilePropertyPusher pusher) {
        return !pusher.pushDirectoriesOnly();
      }
    });

    StartupManager.getInstance(project).registerPreStartupActivity(new Runnable() {
      @Override
      public void run() {
        long l = System.currentTimeMillis();
        pushAll(myPushers);
        LOG.info("File properties pushed in " + (System.currentTimeMillis() - l) + " ms");

        final MessageBusConnection connection = bus.connect();
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
          @Override
          public void rootsChanged(final ModuleRootEvent event) {
            pushAll(myPushers);
            for (FilePropertyPusher pusher : myPushers) {
              pusher.afterRootsChanged(project);
            }
          }
        });

        connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
          @Override
          public void fileCreated(@NotNull final VirtualFileEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            pushRecursively(file, pushers);
          }

          @Override
          public void fileMoved(@NotNull final VirtualFileMoveEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            if (pushers.length == 0) return;
            for (FilePropertyPusher pusher : pushers) {
              file.putUserData(pusher.getFileDataKey(), null);
            }
            // push synchronously to avoid entering dumb mode in the middle of a meaningful write action
            doPushRecursively(file, pushers, ProjectRootManager.getInstance(myProject).getFileIndex());
          }
        }));
        for (final FilePropertyPusher pusher : myPushers) {
          pusher.initExtra(project, bus, new FilePropertyPusher.Engine() {
            @Override
            public void pushAll() {
              PushedFilePropertiesUpdater.this.pushAll(pusher);
            }

            @Override
            public void pushRecursively(VirtualFile file, Project project) {
              PushedFilePropertiesUpdater.this.pushRecursively(file, pusher);
            }
          });
        }
      }
    });
  }

  private void pushRecursively(final VirtualFile dir, final FilePropertyPusher... pushers) {
    if (pushers.length == 0) return;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    queueTask(new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull final ProgressIndicator indicator) {
        doPushRecursively(dir, pushers, fileIndex);
      }
    });
  }

  private void doPushRecursively(VirtualFile dir, final FilePropertyPusher[] pushers, ProjectFileIndex fileIndex) {
    fileIndex.iterateContentUnderDirectory(dir, new ContentIterator() {
      @Override
      public boolean processFile(final VirtualFile fileOrDir) {
        applyPushersToFile(fileOrDir, pushers, null);
        return true;
      }
    });
  }

  private void queueTask(DumbModeTask task) {
    myTasks.offer(task);
    DumbService.getInstance(myProject).queueTask(new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        performPushTasks(indicator);
      }
    });
  }

  public void performPushTasks(ProgressIndicator indicator) {
    while (true) {
      DumbModeTask task = myTasks.poll();
      if (task == null) {
        break;
      }
      task.performInDumbMode(indicator);
    }
  }

  private static <T> T findPusherValuesUpwards(Project project, VirtualFile dir, FilePropertyPusher<T> pusher, T moduleValue) {
    final T value = pusher.getImmediateValue(project, dir);
    if (value != null) return value;
    if (moduleValue != null) return moduleValue;
    final VirtualFile parent = dir.getParent();
    if (parent != null) return findPusherValuesUpwards(project, parent, pusher);
    T projectValue = pusher.getImmediateValue(project, null);
    return projectValue != null? projectValue : pusher.getDefaultValue();
  }

  private static <T> T findPusherValuesUpwards(Project project, VirtualFile dir, FilePropertyPusher<T> pusher) {
    final T userValue = dir.getUserData(pusher.getFileDataKey());
    if (userValue != null) return userValue;
    final T value = pusher.getImmediateValue(project, dir);
    if (value != null) return value;
    final VirtualFile parent = dir.getParent();
    if (parent != null) return findPusherValuesUpwards(project, parent, pusher);
    T projectValue = pusher.getImmediateValue(project, null);
    return projectValue != null ? projectValue : pusher.getDefaultValue();
  }

  public void pushAll(final FilePropertyPusher... pushers) {
    queueTask(new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        doPushAll(pushers);
      }
    });
  }

  private void doPushAll(final FilePropertyPusher[] pushers) {
    Module[] modules = ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      @Override
      public Module[] compute() {
        return ModuleManager.getInstance(myProject).getModules();
      }
    });

    for (final Module module : modules) {
      Runnable iteration = ApplicationManager.getApplication().runReadAction(new Computable<Runnable>() {
        @Override
        public Runnable compute() {
          if (module.isDisposed()) return EmptyRunnable.INSTANCE;
          ProgressManager.checkCanceled();

          final Object[] moduleValues = new Object[pushers.length];
          for (int i = 0; i < moduleValues.length; i++) {
            moduleValues[i] = pushers[i].getImmediateValue(module);
          }

          final ModuleFileIndex fileIndex = ModuleRootManager.getInstance(module).getFileIndex();
          return new Runnable() {
            @Override
            public void run() {
              fileIndex.iterateContent(new ContentIterator() {
                @Override
                public boolean processFile(final VirtualFile fileOrDir) {
                  applyPushersToFile(fileOrDir, pushers, moduleValues);
                  return true;
                }
              });
            }
          };
        }
      });
      iteration.run();
    }
  }

  private void applyPushersToFile(final VirtualFile fileOrDir, final FilePropertyPusher[] pushers, final Object[] moduleValues) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        ProgressManager.checkCanceled();
        if (!fileOrDir.isValid()) return;
        doApplyPushersToFile(fileOrDir, pushers, moduleValues);
      }
    });
  }
  private void doApplyPushersToFile(VirtualFile fileOrDir, FilePropertyPusher[] pushers, Object[] moduleValues) {
    FilePropertyPusher<Object> pusher = null;
    try {
      final boolean isDir = fileOrDir.isDirectory();
      for (int i = 0, pushersLength = pushers.length; i < pushersLength; i++) {
        //noinspection unchecked
        pusher = pushers[i];
        if (!isDir && (pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir))) continue;
        else if (isDir && !pusher.acceptsDirectory(fileOrDir, myProject)) continue;
        findAndUpdateValue(myProject, fileOrDir, pusher, moduleValues != null ? moduleValues[i]:null);
      }
    }
    catch (AbstractMethodError ame) { // acceptsDirectory is missed
      if (pusher != null) throw new ExtensionException(pusher.getClass());
      throw ame;
    }
  }

  public static <T> void findAndUpdateValue(final Project project, final VirtualFile fileOrDir, final FilePropertyPusher<T> pusher, final T moduleValue) {
    final T value = findPusherValuesUpwards(project, fileOrDir, pusher, moduleValue);
    updateValue(fileOrDir, value, pusher);
  }

  private static <T> void updateValue(final VirtualFile fileOrDir, final T value, final FilePropertyPusher<T> pusher) {
    final T oldValue = fileOrDir.getUserData(pusher.getFileDataKey());
    if (value != oldValue) {
      fileOrDir.putUserData(pusher.getFileDataKey(), value);
      try {
        pusher.persistAttribute(fileOrDir, value);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }
}
