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
package com.intellij.openapi.components.impl.stores;

import com.intellij.openapi.components.StateStorage;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

/**
 * @author mike
 */
public class CompoundExternalizationSession {
  private final Map<StateStorage, StateStorage.ExternalizationSession> mySessions = new THashMap<StateStorage, StateStorage.ExternalizationSession>(1);

  @NotNull
  public StateStorage.ExternalizationSession getExternalizationSession(@NotNull StateStorage stateStore) {
    StateStorage.ExternalizationSession session = mySessions.get(stateStore);
    if (session == null) {
      mySessions.put(stateStore, session = stateStore.startExternalization());
    }

    return session;
  }


  @NotNull
  public Collection<StateStorage> getStateStorages() {
    return mySessions.keySet();
  }
}
