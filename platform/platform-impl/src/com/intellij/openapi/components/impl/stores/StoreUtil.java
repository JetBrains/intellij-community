// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StoreUtil {
  private static final Logger LOG = Logger.getInstance(StoreUtil.class);

  private StoreUtil() { }

  public static void save(@NotNull IComponentStore stateStore, @Nullable Project project) {
    save(stateStore, project, false);
  }

  public static void save(@NotNull IComponentStore stateStore, @Nullable Project project, boolean isForce) {
    Thread currentThread = Thread.currentThread();
    ShutDownTracker.getInstance().registerStopperThread(currentThread);
    try {
      stateStore.save(new SmartList<>(), isForce);
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

  /**
   * @param isForceSavingAllSettings Whether to force save non-roamable component configuration.
   */
  public static void saveDocumentsAndProjectsAndApp(boolean isForceSavingAllSettings) {
    FileDocumentManager.getInstance().saveAllDocuments();
    saveProjectsAndApp(isForceSavingAllSettings);
  }

  /**
   * @param isForceSavingAllSettings Whether to force save non-roamable component configuration.
   */
  public static void saveProjectsAndApp(boolean isForceSavingAllSettings) {
    ApplicationManager.getApplication().saveSettings(isForceSavingAllSettings);

    ProjectManager projectManager = ProjectManager.getInstance();
    if (projectManager instanceof ProjectManagerEx) {
      ((ProjectManagerEx)projectManager).flushChangedProjectFileAlarm();
    }

    for (Project project : projectManager.getOpenProjects()) {
      saveProject(project, isForceSavingAllSettings);
    }
  }

  public static void saveProject(@NotNull Project project, boolean isForce) {
    if (isForce && project instanceof ProjectImpl) {
      ((ProjectImpl)project).save(true);
    }
    else {
      project.save();
    }
  }
}
