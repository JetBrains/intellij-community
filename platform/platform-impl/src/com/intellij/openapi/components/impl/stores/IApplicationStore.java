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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;

import java.util.Set;
import java.util.Collection;
import java.io.IOException;

public interface IApplicationStore extends IComponentStore {
  void setOptionsPath(String path);

  StateStorageManager getStateStorageManager();

  void setConfigPath(final String configPath);

  boolean reload(final Set<Pair<VirtualFile, StateStorage>> changedFiles, final Collection<String> notReloadableComponents) throws StateStorage.StateStorageException, IOException;
}
