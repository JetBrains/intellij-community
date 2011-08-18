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
import com.intellij.openapi.components.StateStorageException;
import com.intellij.util.io.fs.IFile;

import java.util.*;

/**
 * @author mike
 */
public class CompoundSaveSession {
  private final Map<StateStorage, StateStorage.SaveSession> mySaveSessions = new HashMap<StateStorage, StateStorage.SaveSession>();

  public CompoundSaveSession(final CompoundExternalizationSession compoundExternalizationSession) {
    final Collection<StateStorage> stateStorages = compoundExternalizationSession.getStateStorages();

    for (StateStorage stateStorage : stateStorages) {
      mySaveSessions.put(stateStorage, stateStorage.startSave(compoundExternalizationSession.getExternalizationSession(stateStorage)));
    }
  }

  public List<IFile> getAllStorageFilesToSave() throws StateStorageException {
    List<IFile> result = new ArrayList<IFile>();

    for (StateStorage stateStorage : mySaveSessions.keySet()) {
      final StateStorage.SaveSession saveSession = mySaveSessions.get(stateStorage);

      result.addAll(saveSession.getStorageFilesToSave());
    }

    return result;
  }

  public void save() throws StateStorageException {
    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      saveSession.save();
    }
  }

  public void finishSave() {
    RuntimeException re = null;
    for (StateStorage stateStorage : mySaveSessions.keySet()) {
      final StateStorage.SaveSession saveSession = mySaveSessions.get(stateStorage);
      try {
        stateStorage.finishSave(saveSession);
      } catch(RuntimeException t) {
        re = t;
      }
    }

    if (re != null) {
      throw re;
    }
  }

  public StateStorage.SaveSession getSaveSession(final StateStorage storage) {
    return mySaveSessions.get(storage);
  }

  public List<IFile> getAllStorageFiles() {
    List<IFile> result = new ArrayList<IFile>();
    for (StateStorage.SaveSession saveSession : mySaveSessions.values()) {
      result.addAll(saveSession.getAllStorageFiles());
    }

    return result;
  }
}
