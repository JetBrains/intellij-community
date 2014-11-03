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
import com.intellij.openapi.components.StateStorage.SaveSession;
import com.intellij.openapi.components.StateStorageException;
import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class StoreUtil {
  private StoreUtil() {
  }

  public static void doSave(@NotNull IComponentStore stateStore) {
    ComponentSaveSession session = stateStore.startSave();
    if (session == null) {
      return;
    }

    ShutDownTracker.getInstance().registerStopperThread(Thread.currentThread());
    try {
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
      try {
        session.finishSave();
      }
      finally {
        ShutDownTracker.getInstance().unregisterStopperThread(Thread.currentThread());
      }
    }
  }
}
