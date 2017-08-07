/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl.stores;

import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StoreUtil {
  private static final Logger LOG = Logger.getInstance(StoreUtil.class);

  private StoreUtil() { }

  public static void save(@NotNull IComponentStore stateStore, @Nullable Project project) {
    Thread currentThread = Thread.currentThread();
    ShutDownTracker.getInstance().registerStopperThread(currentThread);
    try {
      stateStore.save(new SmartList<>());
    }
    catch (IComponentStore.SaveCancelledException e) {
      LOG.info(e);
    }
    catch (Throwable e) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        LOG.error("Save settings failed", e);
      }
      else {
        LOG.warn("Save settings failed", e);
      }

      String messagePostfix = " Please restart " + ApplicationNamesInfo.getInstance().getFullProductName() + "</p>" +
                              (ApplicationManagerEx.getApplicationEx().isInternal() ? "<p>" + StringUtil.getThrowableText(e) + "</p>" : "");

      PluginId pluginId = IdeErrorsDialog.findPluginId(e);
      if (pluginId == null) {
        new Notification("Settings Error", "Unable to save settings",
                         "<p>Failed to save settings." + messagePostfix,
                         NotificationType.ERROR).notify(project);
      }
      else {
        PluginManagerCore.disablePlugin(pluginId.getIdString());

        new Notification("Settings Error", "Unable to save plugin settings",
                         "<p>The plugin <i>" + pluginId + "</i> failed to save settings and has been disabled." + messagePostfix,
                         NotificationType.ERROR).notify(project);
      }
    }
    finally {
      ShutDownTracker.getInstance().unregisterStopperThread(currentThread);
    }
  }

  @NotNull
  public static <T> State getStateSpec(@NotNull PersistentStateComponent<T> persistentStateComponent) {
    return getStateSpecOrError(persistentStateComponent.getClass());
  }

  @NotNull
  public static State getStateSpecOrError(@NotNull Class<? extends PersistentStateComponent> componentClass) {
    State spec = getStateSpec(componentClass);
    if (spec != null) {
      return spec;
    }

    PluginId pluginId = PluginManagerCore.getPluginByClassName(componentClass.getName());
    if (pluginId == null) {
      throw new RuntimeException("No @State annotation found in " + componentClass);
    }
    else {
      throw new PluginException("No @State annotation found in " + componentClass, pluginId);
    }
  }

  @Nullable
  public static State getStateSpec(@NotNull Class<?> aClass) {
    do {
      State stateSpec = aClass.getAnnotation(State.class);
      if (stateSpec != null) {
        return stateSpec;
      }
    }
    while ((aClass = aClass.getSuperclass()) != null);
    return null;
  }

  public static void saveDocumentsAndProjectsAndApp() {
    FileDocumentManager.getInstance().saveAllDocuments();

    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager instanceof ProjectManagerEx) {
      ((ProjectManagerEx)projectManager).flushChangedProjectFileAlarm();
    }

    for (Project openProject : projectManager.getOpenProjects()) {
      openProject.save();
    }

    ApplicationManager.getApplication().saveSettings();
  }
}
