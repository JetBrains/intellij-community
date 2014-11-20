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
package com.intellij.openapi.components.impl.stores;

import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StoreUtil {
  private StoreUtil() {
  }

  public static void doSave(@NotNull IComponentStore stateStore) {
    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    try {
      ComponentSaveSession session = stateStore.startSave();
      if (session == null) {
        return;
      }

      List<Pair<SaveSession, VirtualFile>> readonlyFiles = new SmartList<Pair<SaveSession, VirtualFile>>();
      session.save(readonlyFiles);
    }
    catch (Throwable e) {
      PluginId pluginId = IdeErrorsDialog.findPluginId(e);
      if (pluginId == null) {
        //noinspection InstanceofCatchParameter
        if (e instanceof RuntimeException) {
          throw ((RuntimeException)e);
        }
        else {
          throw new StateStorageException(e);
        }
      }
      else {
        throw new PluginException(e, pluginId);
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
    if (pluginId != null) {
      throw new PluginException("No @State annotation found in " + componentClass, pluginId);
    }
    throw new RuntimeException("No @State annotation found in " + componentClass);
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
}
