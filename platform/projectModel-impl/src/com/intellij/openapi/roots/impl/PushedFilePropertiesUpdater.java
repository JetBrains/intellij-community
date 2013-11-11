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

/*
 * @author max
 */
package com.intellij.openapi.roots.impl;

import com.intellij.ProjectTopics;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;

import java.io.IOException;

public class PushedFilePropertiesUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater");

  private final Project myProject;
  private final FilePropertyPusher[] myPushers;
  private final FilePropertyPusher[] myFilePushers;

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
          public void fileCreated(final VirtualFileEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            pushRecursively(file, project, pushers);
          }

          @Override
          public void fileMoved(final VirtualFileMoveEvent event) {
            final VirtualFile file = event.getFile();
            final FilePropertyPusher[] pushers = file.isDirectory() ? myPushers : myFilePushers;
            for (FilePropertyPusher pusher : pushers) {
              file.putUserData(pusher.getFileDataKey(), null);
            }
            pushRecursively(file, project, pushers);
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
              PushedFilePropertiesUpdater.this.pushRecursively(file, project, pusher);
            }
          });
        }
      }
    });
  }

  public void pushRecursively(final VirtualFile dir, final Project project, final FilePropertyPusher... pushers) {
    if (pushers.length == 0) return;
    ProjectRootManager.getInstance(project).getFileIndex().iterateContentUnderDirectory(dir, new ContentIterator() {
      @Override
      public boolean processFile(final VirtualFile fileOrDir) {
        applyPushersToFile(fileOrDir, pushers, null);
        return true;
      }
    });
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
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText("Updating file properties...");
    }
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (int i1 = 0; i1 < modules.length; i1++) {
      if (indicator != null) {
        indicator.setFraction((double) i1 / modules.length);
      }
      Module module = modules[i1];
      final Object[] moduleValues = new Object[pushers.length];
      for (int i = 0; i < moduleValues.length; i++) {
        moduleValues[i] = pushers[i].getImmediateValue(module);
      }
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      final ModuleFileIndex index = rootManager.getFileIndex();
      for (VirtualFile root : rootManager.getContentRoots()) {
        index.iterateContentUnderDirectory(root, new ContentIterator() {
          @Override
          public boolean processFile(final VirtualFile fileOrDir) {
            applyPushersToFile(fileOrDir, pushers, moduleValues);
            return true;
          }
        });
      }
    }
    if (indicator != null) {
      indicator.popState();
    }

  }

  private void applyPushersToFile(VirtualFile fileOrDir, FilePropertyPusher[] pushers, Object[] moduleValues) {
    FilePropertyPusher<Object> pusher = null;
    try {
      final boolean isDir = fileOrDir.isDirectory();
      for (int i = 0, pushersLength = pushers.length; i < pushersLength; i++) {
        pusher = pushers[i];
        if (!isDir && (pusher.pushDirectoriesOnly() || !pusher.acceptsFile(fileOrDir))) continue;
        else if (isDir && !pusher.acceptsDirectory(fileOrDir, myProject)) continue;
        findAndUpdateValue(myProject, fileOrDir, pusher, moduleValues != null ? moduleValues[i]:null);
      }
    }
    catch (AbstractMethodError ame) { // acceptsDirectory is missed
      PluginId pluginId = pusher != null ? PluginManagerCore.getPluginByClassName(pusher.getClass().getName()) : null;
      if (pluginId != null) throw new PluginException("Incompatible plugin", ame, pluginId);
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
