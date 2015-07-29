/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class StoreUtil {
  private static final Logger LOG = Logger.getInstance(StoreUtil.class);

  private StoreUtil() {
  }

  public static void save(@NotNull IComponentStore stateStore, @Nullable Project project) {
    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    try {
      stateStore.save(new SmartList<Pair<SaveSession, VirtualFile>>());
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
      ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
    }
  }

  @NotNull
  public static <T> State getStateSpec(@NotNull PersistentStateComponent<T> persistentStateComponent) {
    Class<? extends PersistentStateComponent> componentClass = persistentStateComponent.getClass();
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

  @NotNull
  public static String getComponentName(@NotNull PersistentStateComponent<?> persistentStateComponent) {
    return getStateSpec(persistentStateComponent).name();
  }

  public enum ReloadComponentStoreStatus {
    RESTART_AGREED,
    RESTART_CANCELLED,
    ERROR,
    SUCCESS,
  }

  @NotNull
  public static ReloadComponentStoreStatus reloadStore(@NotNull MultiMap<StateStorage, VirtualFile> changes, @NotNull IComponentStore store) {
    Collection<String> notReloadableComponents;
    boolean willBeReloaded = false;
    try {
      AccessToken token = WriteAction.start();
      try {
        notReloadableComponents = store.reload(changes);
      }
      catch (Throwable e) {
        Messages.showWarningDialog(ProjectBundle.message("project.reload.failed", e.getMessage()),
                                   ProjectBundle.message("project.reload.failed.title"));
        return ReloadComponentStoreStatus.ERROR;
      }
      finally {
        token.finish();
      }

      if (ContainerUtil.isEmpty(notReloadableComponents)) {
        return ReloadComponentStoreStatus.SUCCESS;
      }

      willBeReloaded = askToRestart(store, notReloadableComponents, changes);
      return willBeReloaded ? ReloadComponentStoreStatus.RESTART_AGREED : ReloadComponentStoreStatus.RESTART_CANCELLED;
    }
    finally {
      if (!willBeReloaded) {
        for (StateStorage storage : changes.keySet()) {
          if (storage instanceof StateStorageBase) {
            ((StateStorageBase)storage).enableSaving();
          }
        }
      }
    }
  }

  // used in settings repository plugin
  public static boolean askToRestart(@NotNull IComponentStore store,
                                     @NotNull Collection<String> notReloadableComponents,
                                     @Nullable MultiMap<StateStorage, VirtualFile> changedStorages) {
    StringBuilder message = new StringBuilder();
    String storeName = store instanceof IProjectStore ? "Project" : "Application";
    message.append(storeName).append(' ');
    message.append("components were changed externally and cannot be reloaded:\n\n");
    int count = 0;
    for (String component : notReloadableComponents) {
      if (count == 10) {
        message.append('\n').append("and ").append(notReloadableComponents.size() - count).append(" more").append('\n');
      }
      else {
        message.append(component).append('\n');
        count++;
      }
    }

    message.append("\nWould you like to ");
    if (store instanceof IProjectStore) {
      message.append("reload project?");
    }
    else {
      message.append(ApplicationManager.getApplication().isRestartCapable() ? "restart" : "shutdown").append(' ');
      message.append(ApplicationNamesInfo.getInstance().getProductName()).append('?');
    }

    if (Messages.showYesNoDialog(message.toString(),
                                 storeName + " Files Changed", Messages.getQuestionIcon()) == Messages.YES) {
      if (changedStorages != null) {
        for (StateStorage storage : changedStorages.keySet()) {
          if (storage instanceof StateStorageBase) {
            ((StateStorageBase)storage).disableSaving();
          }
        }
      }
      return true;
    }
    return false;
  }
}
