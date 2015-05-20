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

package com.intellij.lang;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public interface PerFileMappings<T> {

  @NotNull
  Map<VirtualFile, T> getMappings();

  void setMappings(@NotNull Map<VirtualFile, T> mappings);

  void setMapping(@Nullable VirtualFile file, T value);

  Collection<T> getAvailableValues(VirtualFile file);

  @Nullable
  T getMapping(VirtualFile file);

  @Nullable
  T getDefaultMapping(@Nullable VirtualFile file);

  T chosenToStored(VirtualFile file, T value);

  boolean isSelectable(T value);
}
