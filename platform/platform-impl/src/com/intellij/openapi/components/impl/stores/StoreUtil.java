/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.components.store.ComponentSaveSession;
import com.intellij.openapi.util.Pair;
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
    ComponentSaveSession session = null;
    try {
      session = stateStore.startSave();
      List<Pair<StateStorageManager.SaveSession, VirtualFile>> readonlyFiles = new SmartList<Pair<StateStorageManager.SaveSession, VirtualFile>>();
      session.save(readonlyFiles);
    }
    finally {
      if (session != null) {
        session.finishSave();
      }
    }
  }
}
