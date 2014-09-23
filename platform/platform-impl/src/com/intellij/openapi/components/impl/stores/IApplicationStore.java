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

import com.intellij.openapi.components.StateStorage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public interface IApplicationStore extends IComponentStore {
  void setOptionsPath(@NotNull String path);

  @NotNull
  String getConfigPath();

  void setConfigPath(@NotNull String configPath);

  /**
   * null if reloaded
   * empty list if nothing to reload
   * list of not reloadable components (reload is not performed)
   */
  @Nullable
  Collection<String> reload(@NotNull Set<Pair<VirtualFile, StateStorage>> changedFiles) throws IOException;
}
